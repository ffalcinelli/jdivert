package com.github.ffalcinelli.jdivert.ebpfdivert;

import com.github.ffalcinelli.jdivert.WinDivertAsyncResult;
import com.github.ffalcinelli.jdivert.ebpfdivert.jna.LibBpf;
import com.github.ffalcinelli.jdivert.ebpfdivert.jna.LibC;
import com.github.ffalcinelli.jdivert.exceptions.WinDivertException;
import com.github.ffalcinelli.jdivert.windivert.DeployHandler;
import com.github.ffalcinelli.jdivert.windivert.NativeAdapter;
import com.github.ffalcinelli.jdivert.windivert.WinDivertAddress;
import com.sun.jna.Memory;
import com.sun.jna.Pointer;
import com.sun.jna.ptr.PointerByReference;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

/**
 * eBPF implementation of NativeAdapter for Linux using standard libbpf and libc.
 */
public class EBPFDivertJnaNativeAdapter implements NativeAdapter {

    private static final LibBpf lib = LibBpf.INSTANCE;
    private static final LibC libc = LibC.INSTANCE;
    private static LibBpf.libbpf_print_fn_t printCallbackRef;

    static {
        try {
            printCallbackRef = (level, format, args) -> {
                PointerByReference ptrRef = new PointerByReference();
                if (libc.vasprintf(ptrRef, format, args) >= 0) {
                    Pointer ptr = ptrRef.getValue();
                    String msg = ptr.getString(0);
                    libc.free(ptr);

                    if (msg.contains("Invalid handle") || 
                        msg.contains("Exclusivity flag on") || 
                        msg.contains("Cannot find specified qdisc") || 
                        msg.contains("Kernel error message")) {
                        return 0;
                    }
                    System.err.print("libbpf JNA: " + msg);
                }
                return 0;
            };
            lib.libbpf_set_print(printCallbackRef);
        } catch (Throwable t) {
            // Ignore if we can't load/set print callback
        }
    }

    private static final ExecutorService executor = Executors.newCachedThreadPool(new ThreadFactory() {
        @Override
        public Thread newThread(Runnable r) {
            Thread t = new Thread(r, "EBPFDivertJnaNativeAdapter-AsyncThread");
            t.setDaemon(true);
            return t;
        }
    });

    private static class EBPFAsyncImplementation implements WinDivertAsyncResult.AsyncImplementation {
        private final CompletableFuture<Integer> future = new CompletableFuture<>();
        private final Future<?> task;

        public EBPFAsyncImplementation(EBPFHandle handle, JNABuffer buffer, WinDivertAddress address, Map<ByteBuffer, byte[]> l2Headers) {
            this.task = executor.submit(() -> {
                try {
                    PacketEvent event = handle.packetQueue.poll();
                    while (event == null && !Thread.currentThread().isInterrupted()) {
                        lib.ring_buffer__poll(handle.ringBuffer, 10);
                        event = handle.packetQueue.poll(10, TimeUnit.MILLISECONDS);
                    }
                    if (event != null) {
                        int l2Len = event.l2Len;
                        int payloadLen = event.data.length - l2Len;
                        if (payloadLen > buffer.capacity()) {
                            payloadLen = buffer.capacity();
                        }

                        ByteBuffer bb = buffer.getByteBuffer();
                        bb.clear();
                        bb.put(event.data, l2Len, payloadLen);
                        bb.flip();

                        byte[] l2Header = new byte[l2Len];
                        System.arraycopy(event.data, 0, l2Header, 0, l2Len);
                        l2Headers.put(bb, l2Header);

                        address.Union.Network.IfIdx = event.ifindex;
                        address.setOutbound(event.direction == 2);
                        future.complete(payloadLen);
                    } else {
                        future.complete(0);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    future.completeExceptionally(e);
                } catch (Throwable t) {
                    future.completeExceptionally(t);
                }
            });
        }

        @Override
        public boolean isCompleted() {
            return future.isDone();
        }

        @Override
        public int waitAndGetResult() throws WinDivertException {
            try {
                return future.get();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new WinDivertException(-1, "Async receive interrupted", e);
            } catch (ExecutionException e) {
                throw new WinDivertException(-1, "Async receive failed", e.getCause());
            }
        }
    }

    // Cache to associate packet direct ByteBuffers with their captured Layer 2 headers
    private static final Map<ByteBuffer, byte[]> l2Headers = Collections.synchronizedMap(new WeakHashMap<>());

    private static class AttachedHook {
        LibBpf.BpfTcHook hook;
        LibBpf.BpfTcOpts opts;

        AttachedHook(LibBpf.BpfTcHook hook, LibBpf.BpfTcOpts opts) {
            this.hook = hook;
            this.opts = opts;
        }
    }

    private static class EBPFHandle implements Handle {
        Pointer bpfObj;
        Pointer ringBuffer;
        int tcPriority;
        List<AttachedHook> hooks = new ArrayList<>();
        Map<String, Integer> rawSocks = new HashMap<>();
        int rawSockV4 = -1;
        int rawSockV6 = -1;
        int maxQueueSize = 4096;
        BlockingQueue<PacketEvent> packetQueue = new LinkedBlockingQueue<>();
        LibBpf.ring_buffer_sample_fn callback;

        @Override
        public void close() throws WinDivertException {
            try {
                // Detach all TC hooks
                for (AttachedHook ah : hooks) {
                    lib.bpf_tc_detach(ah.hook, ah.opts);
                    lib.bpf_tc_hook_destroy(ah.hook);
                }
                hooks.clear();

                // Close raw AF_PACKET sockets
                for (int fd : rawSocks.values()) {
                    libc.close(fd);
                }
                rawSocks.clear();

                // Close fallback raw IP sockets
                if (rawSockV4 >= 0) {
                    libc.close(rawSockV4);
                    rawSockV4 = -1;
                }
                if (rawSockV6 >= 0) {
                    libc.close(rawSockV6);
                    rawSockV6 = -1;
                }

                if (ringBuffer != null) lib.ring_buffer__free(ringBuffer);
                if (bpfObj != null) lib.bpf_object__close(bpfObj);
            } catch (Exception e) {
                throw new WinDivertException(-1, "Failed to close BPF handle", e);
            }
        }

        @Override
        public boolean isValid() {
            return bpfObj != null;
        }
    }

    private static class JNABuffer implements Buffer {
        private final ByteBuffer byteBuffer;

        JNABuffer(int size) {
            this.byteBuffer = ByteBuffer.allocateDirect(size);
        }

        @Override
        public ByteBuffer getByteBuffer() {
            return byteBuffer;
        }

        @Override
        public int capacity() {
            return byteBuffer.capacity();
        }

        @Override
        public void close() {
        }
    }

    private static class PacketEvent {
        byte[] data;
        int ifindex;
        short direction;
        short l2Len;

        PacketEvent(byte[] data, int ifindex, short direction, short l2Len) {
            this.data = data;
            this.ifindex = ifindex;
            this.direction = direction;
            this.l2Len = l2Len;
        }
    }

    @Override
    public Handle open(String filter, int layer, short priority, long flags) throws WinDivertException {
        Path bpfObjPath = DeployHandler.deployToPath();
        Pointer obj = lib.bpf_object__open_file(bpfObjPath.toString(), null);
        if (obj == null) throw new WinDivertException(-1, "Failed to open BPF object");

        if (lib.bpf_object__load(obj) != 0) {
            lib.bpf_object__close(obj);
            throw new WinDivertException(-1, "Failed to load BPF object");
        }

        EBPFHandle handle = new EBPFHandle();
        handle.bpfObj = obj;

        if (priority == 0) {
            handle.tcPriority = 30000;
        } else {
            handle.tcPriority = 30001 - priority;
        }

        // Configure maps
        Pointer cfgMap = lib.bpf_object__find_map_by_name(obj, "config_map");
        if (cfgMap != null) {
            int cfgFd = lib.bpf_map__fd(cfgMap);
            Memory key = new Memory(4);
            key.setInt(0, 0);
            Memory val = new Memory(12);
            val.setInt(0, handle.tcPriority);
            val.setInt(4, 2048);
            val.setInt(8, 0x4D490000);
            lib.bpf_map_update_elem(cfgFd, key, val, 0);
        }

        // Setup filter rules
        Pointer map = lib.bpf_object__find_map_by_name(obj, "filter_rules");
        Pointer mapV6 = lib.bpf_object__find_map_by_name(obj, "filter_rules_ipv6");
        if (map != null && mapV6 != null) {
            int rulesFd = lib.bpf_map__fd(map);
            int rulesFdIpv6 = lib.bpf_map__fd(mapV6);

            // Clear maps
            Memory key = new Memory(4);
            Memory emptyRule = new Memory(34);
            emptyRule.write(0, new byte[34], 0, 34);
            Memory emptyRuleV6 = new Memory(82);
            emptyRuleV6.write(0, new byte[82], 0, 82);

            for (int i = 0; i < 64; i++) {
                key.setInt(0, i);
                lib.bpf_map_update_elem(rulesFd, key, emptyRule, 0);
                lib.bpf_map_update_elem(rulesFdIpv6, key, emptyRuleV6, 0);
            }

            // Write transpiled rules
            boolean sniff = (flags & 1) != 0; // Flag.SNIFF = 1
            boolean drop = (flags & 2) != 0;  // Flag.DROP = 2
            List<FilterTranspiler.TranspiledRule> rules = FilterTranspiler.transpile(filter, sniff, drop);
            for (int i = 0; i < Math.min(rules.size(), 64); i++) {
                FilterTranspiler.TranspiledRule r = rules.get(i);
                key.setInt(0, i);
                Memory val = new Memory(r.ruleBytes.length);
                val.write(0, r.ruleBytes, 0, r.ruleBytes.length);
                if (r.isIpv6) {
                    lib.bpf_map_update_elem(rulesFdIpv6, key, val, 0);
                } else {
                    lib.bpf_map_update_elem(rulesFd, key, val, 0);
                }
            }
        }

        // Setup TC Ingress / Egress hook attach
        Pointer progIngress = lib.bpf_object__find_program_by_name(obj, "tc_divert_ingress");
        Pointer progEgress = lib.bpf_object__find_program_by_name(obj, "tc_divert_egress");
        if (progIngress == null || progEgress == null) {
            lib.bpf_object__close(obj);
            throw new WinDivertException(-1, "Required ingress/egress BPF programs not found");
        }

        int progIngressFd = lib.bpf_program__fd(progIngress);
        int progEgressFd = lib.bpf_program__fd(progEgress);

        List<Integer> interfaceIndexes = new ArrayList<>();
        try {
            java.util.Enumeration<java.net.NetworkInterface> nets = java.net.NetworkInterface.getNetworkInterfaces();
            while (nets.hasMoreElements()) {
                java.net.NetworkInterface net = nets.nextElement();
                if (net.getIndex() > 0) {
                    interfaceIndexes.add(net.getIndex());
                }
            }
        } catch (java.net.SocketException e) {
            lib.bpf_object__close(obj);
            throw new WinDivertException(-1, "Failed to resolve network interfaces", e);
        }

        for (int ifindex : interfaceIndexes) {
            // Attach ingress
            LibBpf.BpfTcHook hookIngress = new LibBpf.BpfTcHook();
            hookIngress.ifindex = ifindex;
            hookIngress.attach_point = 1; // Ingress
            lib.bpf_tc_hook_create(hookIngress);

            LibBpf.BpfTcOpts optsIngress = new LibBpf.BpfTcOpts();
            optsIngress.prog_fd = progIngressFd;
            optsIngress.priority = handle.tcPriority;

            if (lib.bpf_tc_attach(hookIngress, optsIngress) == 0) {
                handle.hooks.add(new AttachedHook(hookIngress, optsIngress));
            }

            // Attach egress
            LibBpf.BpfTcHook hookEgress = new LibBpf.BpfTcHook();
            hookEgress.ifindex = ifindex;
            hookEgress.attach_point = 2; // Egress
            lib.bpf_tc_hook_create(hookEgress);

            LibBpf.BpfTcOpts optsEgress = new LibBpf.BpfTcOpts();
            optsEgress.prog_fd = progEgressFd;
            optsEgress.priority = handle.tcPriority;

            if (lib.bpf_tc_attach(hookEgress, optsEgress) == 0) {
                handle.hooks.add(new AttachedHook(hookEgress, optsEgress));
            }
        }

        // Setup Ring Buffer callback
        Pointer rbMap = lib.bpf_object__find_map_by_name(obj, "pcap_ringbuf");
        if (rbMap != null) {
            int rbFd = lib.bpf_map__fd(rbMap);
            handle.callback = (ctx, data, size) -> {
                int pktLen = data.getInt(0);
                int ifindex = data.getInt(4);
                short direction = data.getShort(8);
                short l2Len = data.getShort(10);

                byte[] pktData = data.getByteArray(16, pktLen);
                if (handle.packetQueue.size() < handle.maxQueueSize) {
                    handle.packetQueue.offer(new PacketEvent(pktData, ifindex, direction, l2Len));
                }
                return 0;
            };
            handle.ringBuffer = lib.ring_buffer__new(rbFd, handle.callback, null, null);
        }

        return handle;
    }

    @Override
    public int recv(Handle handle, Buffer buffer, WinDivertAddress address) throws WinDivertException {
        EBPFHandle h = (EBPFHandle) handle;
        try {
            PacketEvent event = h.packetQueue.poll();
            if (event == null) {
                lib.ring_buffer__poll(h.ringBuffer, 10);
                event = h.packetQueue.poll(10, TimeUnit.MILLISECONDS);
            }
            if (event != null) {
                int l2Len = event.l2Len;
                int payloadLen = event.data.length - l2Len;
                if (payloadLen > buffer.capacity()) {
                    payloadLen = buffer.capacity();
                }

                ByteBuffer bb = buffer.getByteBuffer();
                bb.clear();
                bb.put(event.data, l2Len, payloadLen);
                bb.flip();

                // Cache L2 header associated with this buffer
                byte[] l2Header = new byte[l2Len];
                System.arraycopy(event.data, 0, l2Header, 0, l2Len);
                l2Headers.put(bb, l2Header);

                address.Union.Network.IfIdx = event.ifindex;
                address.setOutbound(event.direction == 2); // 2 = Egress/Outbound
                return payloadLen;
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return 0;
    }

    @Override
    public <T> WinDivertAsyncResult<T> recvAsync(Handle handle, int bufsize, WinDivertAsyncResult.ResultConverter<T> converter) throws WinDivertException {
        Objects.requireNonNull(handle, "handle cannot be null");
        JNABuffer buffer = new JNABuffer(bufsize);
        WinDivertAddress address = new WinDivertAddress();
        EBPFAsyncImplementation impl = new EBPFAsyncImplementation((EBPFHandle) handle, buffer, address, l2Headers);
        return new WinDivertAsyncResult<>(handle, buffer, address, converter, impl);
    }

    @Override
    public int send(Handle handle, ByteBuffer packet, WinDivertAddress address) throws WinDivertException {
        EBPFHandle h = (EBPFHandle) handle;

        int ifindex = address.Union.Network.IfIdx;
        int direction = address.isOutbound() ? 2 : 1; // 2 = Egress, 1 = Ingress

        byte[] l2Header = l2Headers.get(packet);
        if (l2Header != null && l2Header.length > 0) {
            // Re-inject via raw AF_PACKET socket
            String sockKey = direction + "-" + ifindex;
            int sock;
            synchronized (h.rawSocks) {
                if (!h.rawSocks.containsKey(sockKey)) {
                    // AF_PACKET = 17, SOCK_RAW = 3, htons(ETH_P_ALL) = 0x0300
                    int s = libc.socket(17, 3, (short) 0x0300);
                    if (s < 0) {
                        throw new WinDivertException(-1, "Failed to create raw packet socket");
                    }

                    boolean isRedirect = (direction == 1);
                    int loIdx = 1;
                    try {
                        java.net.NetworkInterface lo = java.net.NetworkInterface.getByName("lo");
                        if (lo != null) loIdx = lo.getIndex();
                    } catch (Exception ignored) {
                    }

                    int bindIfindex = isRedirect ? loIdx : ifindex;
                    if (ifindex == loIdx) {
                        isRedirect = true;
                        bindIfindex = loIdx;
                    }

                    int mark = isRedirect ? (0x4D4A0000 | (ifindex & 0xFFFF)) : (0x4D490000 | (h.tcPriority & 0xFFFF));
                    Memory markMem = new Memory(4);
                    markMem.setInt(0, mark);
                    if (libc.setsockopt(s, 1, 36, markMem, 4) < 0) {
                        libc.close(s);
                        throw new WinDivertException(-1, "Failed to set SO_MARK");
                    }

                    LibC.sockaddr_ll sll = new LibC.sockaddr_ll();
                    sll.sll_ifindex = bindIfindex;
                    if (libc.bind(s, sll, sll.size()) < 0) {
                        libc.close(s);
                        throw new WinDivertException(-1, "Failed to bind raw packet socket");
                    }
                    h.rawSocks.put(sockKey, s);
                }
                sock = h.rawSocks.get(sockKey);
            }

            // Construct full Ethernet frame
            int payloadLen = packet.remaining();
            byte[] fullFrame = new byte[l2Header.length + payloadLen];
            System.arraycopy(l2Header, 0, fullFrame, 0, l2Header.length);
            packet.get(fullFrame, l2Header.length, payloadLen);

            ByteBuffer fullFrameBuffer = ByteBuffer.allocateDirect(fullFrame.length);
            fullFrameBuffer.put(fullFrame);
            fullFrameBuffer.flip();

            int sent = libc.send(sock, fullFrameBuffer, fullFrame.length, 0);
            if (sent < 0) {
                throw new WinDivertException(-1, "Failed to send packet via AF_PACKET raw socket");
            }
            return payloadLen;
        }

        // Fallback: Send raw L3 IP packet via standard raw IP socket
        boolean isIpv6 = false;
        if (packet.remaining() > 0) {
            byte ver = (byte) ((packet.get(packet.position()) >> 4) & 0x0F);
            if (ver == 6) isIpv6 = true;
        }

        int sock;
        if (isIpv6) {
            if (h.rawSockV6 < 0) {
                // AF_INET6 = 10, SOCK_RAW = 3, IPPROTO_RAW = 255
                h.rawSockV6 = libc.socket(10, 3, 255);
                if (h.rawSockV6 < 0) {
                    throw new WinDivertException(-1, "Failed to create IPv6 raw IP socket");
                }
                Memory markMem = new Memory(4);
                markMem.setInt(0, 0x4D490000 | (h.tcPriority & 0xFFFF));
                libc.setsockopt(h.rawSockV6, 1, 36, markMem, 4); // SOL_SOCKET = 1, SO_MARK = 36
            }
            sock = h.rawSockV6;
        } else {
            if (h.rawSockV4 < 0) {
                // AF_INET = 2, SOCK_RAW = 3, IPPROTO_RAW = 255
                h.rawSockV4 = libc.socket(2, 3, 255);
                if (h.rawSockV4 < 0) {
                    throw new WinDivertException(-1, "Failed to create IPv4 raw IP socket");
                }
                Memory markMem = new Memory(4);
                markMem.setInt(0, 0x4D490000 | (h.tcPriority & 0xFFFF));
                libc.setsockopt(h.rawSockV4, 1, 36, markMem, 4); // SOL_SOCKET = 1, SO_MARK = 36

                Memory hdrIncl = new Memory(4);
                hdrIncl.setInt(0, 1);
                libc.setsockopt(h.rawSockV4, 0, 2, hdrIncl, 4); // IPPROTO_IP = 0, IP_HDRINCL = 2
            }
            sock = h.rawSockV4;
        }

        int payloadLen = packet.remaining();
        if (isIpv6) {
            // Parse IPv6 destination address
            byte[] dstIpBytes = new byte[16];
            for (int i = 0; i < 16; i++) {
                dstIpBytes[i] = packet.get(packet.position() + 24 + i);
            }
            LibC.sockaddr_in6 sin6 = new LibC.sockaddr_in6();
            System.arraycopy(dstIpBytes, 0, sin6.sin6_addr, 0, 16);

            int sent = libc.sendto(sock, packet, payloadLen, 0, sin6, sin6.size());
            if (sent < 0) {
                throw new WinDivertException(-1, "Failed to send IPv6 raw packet");
            }
        } else {
            // Parse IPv4 destination address
            int dstIpInt = packet.getInt(packet.position() + 16);
            LibC.sockaddr_in sin = new LibC.sockaddr_in();
            sin.sin_addr = dstIpInt;

            int sent = libc.sendto(sock, packet, payloadLen, 0, sin, sin.size());
            if (sent < 0) {
                throw new WinDivertException(-1, "Failed to send IPv4 raw packet");
            }
        }

        return payloadLen;
    }

    @Override
    public <T> WinDivertAsyncResult<T> sendAsync(Handle handle, ByteBuffer packet, WinDivertAddress address, WinDivertAsyncResult.ResultConverter<T> converter) throws WinDivertException {
        Objects.requireNonNull(handle, "handle cannot be null");
        Objects.requireNonNull(packet, "packet cannot be null");
        Objects.requireNonNull(address, "address cannot be null");
        JNABuffer jnaBuffer = new JNABuffer(packet.remaining());
        jnaBuffer.getByteBuffer().put(packet.duplicate());
        jnaBuffer.getByteBuffer().flip();

        CompletableFuture<Integer> sendFuture = new CompletableFuture<>();
        executor.submit(() -> {
            try {
                int len = send(handle, jnaBuffer.getByteBuffer(), address);
                sendFuture.complete(len);
            } catch (Throwable t) {
                sendFuture.completeExceptionally(t);
            }
        });

        WinDivertAsyncResult.AsyncImplementation impl = new WinDivertAsyncResult.AsyncImplementation() {
            @Override
            public boolean isCompleted() {
                return sendFuture.isDone();
            }

            @Override
            public int waitAndGetResult() throws WinDivertException {
                try {
                    return sendFuture.get();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new WinDivertException(-1, "Async send interrupted", e);
                } catch (ExecutionException e) {
                    throw new WinDivertException(-1, "Async send failed", e.getCause());
                }
            }
        };

        return new WinDivertAsyncResult<>(handle, jnaBuffer, address, converter, impl);
    }

    @Override
    public void shutdown(Handle handle, int how) throws WinDivertException {
        try {
            handle.close();
        } catch (Exception e) {
            throw new WinDivertException(-1, "Failed to shutdown handle", e);
        }
    }

    @Override
    public void setParam(Handle handle, int param, long value) throws WinDivertException {
        if (handle == null) throw new WinDivertException(-1, "Handle cannot be null");
        EBPFHandle h = (EBPFHandle) handle;
        if (param == 0) { // Param.QUEUE_LEN
            h.maxQueueSize = (int) value;
        } else {
            throw new WinDivertException(-1, "Parameter not supported on eBPF backend");
        }
    }

    @Override
    public long getParam(Handle handle, int param) throws WinDivertException {
        if (handle == null) throw new WinDivertException(-1, "Handle cannot be null");
        EBPFHandle h = (EBPFHandle) handle;
        if (param == 0) { // Param.QUEUE_LEN
            return h.maxQueueSize;
        } else {
            throw new WinDivertException(-1, "Parameter not supported on eBPF backend");
        }
    }

    @Override
    public int calcChecksums(byte[] packet, WinDivertAddress address, long flags) {
        return 0;
    }

    @Override
    public long hashPacket(byte[] packet, long seed) {
        return 0;
    }

    @Override
    public Buffer allocateBuffer(int size) {
        return new JNABuffer(size);
    }

    @Override
    public String formatMessage(int errorCode) {
        return "Error " + errorCode;
    }

    @Override
    public int getLastError() {
        return 0;
    }
}
