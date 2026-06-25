package com.github.ffalcinelli.jdivert.ebpfdivert;

import com.github.ffalcinelli.jdivert.WinDivertAsyncResult;
import com.github.ffalcinelli.jdivert.ebpfdivert.jna.LibBpf;
import com.github.ffalcinelli.jdivert.exceptions.WinDivertException;
import com.github.ffalcinelli.jdivert.windivert.DeployHandler;
import com.github.ffalcinelli.jdivert.windivert.NativeAdapter;
import com.github.ffalcinelli.jdivert.windivert.WinDivertAddress;
import com.sun.jna.Memory;
import com.sun.jna.Pointer;

import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * eBPF implementation of NativeAdapter for Linux using JNA.
 */
public class EBPFDivertJnaNativeAdapter implements NativeAdapter {

    private static final LibBpf lib = LibBpf.INSTANCE;

    private static class EBPFHandle implements Handle {
        Pointer bpfObj;
        Pointer ringBuffer;
        int filterMapFd;
        int maxQueueSize = 4096;
        BlockingQueue<PacketEvent> packetQueue = new LinkedBlockingQueue<>();
        LibBpf.ring_buffer_sample_fn callback;

        @Override
        public void close() throws WinDivertException {
            try {
                if (ringBuffer != null) lib.ring_buffer__free(ringBuffer);
                if (bpfObj != null) lib.bpf_object__close(bpfObj);
            } catch (Exception e) {
                throw new WinDivertException(-1, 
"Failed to close BPF handle", e);
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
        LibBpf.divert_pkt_header header;

        PacketEvent(byte[] data, LibBpf.divert_pkt_header header) {
            this.data = data;
            this.header = header;
        }
    }

    @Override
    public Handle open(String filter, int layer, short priority, long flags) throws WinDivertException {
        Path bpfObjPath = DeployHandler.deployToPath();
        Pointer obj = lib.bpf_object__open_file(bpfObjPath.toString(), null);
        if (obj == null) throw new WinDivertException(-1, "Failed to open BPF object");

        if (lib.bpf_object__load(obj) != 0) {
            lib.bpf_object__close(obj);
            throw new WinDivertException(-1, 
"Failed to load BPF object");
        }

        EBPFHandle handle = new EBPFHandle();
        handle.bpfObj = obj;

        // Attach programs (simplified: attach all programs found in the object)
        // In a real implementation, we would attach specific TC programs to interfaces.
        Pointer progIngress = lib.bpf_object__find_program_by_name(obj, "tc_divert_ingress");
        if (progIngress != null) lib.bpf_program__attach(progIngress);
        Pointer progEgress = lib.bpf_object__find_program_by_name(obj, "tc_divert_egress");
        if (progEgress != null) lib.bpf_program__attach(progEgress);

        // Setup filter rules
        Pointer map = lib.bpf_object__find_map_by_name(obj, "filter_rules");
        if (map != null) {
            handle.filterMapFd = lib.bpf_map__fd(map);
            List<FilterTranspiler.BpfFilterRule> rules = FilterTranspiler.transpile(filter);
            for (int i = 0; i < rules.size(); i++) {
                byte[] ruleBytes = rules.get(i).toBytes();
                Memory key = new Memory(4);
                key.setInt(0, i);
                Memory val = new Memory(ruleBytes.length);
                val.write(0, ruleBytes, 0, ruleBytes.length);
                lib.bpf_map_update_elem(handle.filterMapFd, key, val, 0);
            }
        }

        // Setup Ring Buffer
        Pointer rbMap = lib.bpf_object__find_map_by_name(obj, "pcap_ringbuf");
        if (rbMap != null) {
            int rbFd = lib.bpf_map__fd(rbMap);
            handle.callback = (ctx, data, size) -> {
                LibBpf.divert_pkt_header header = new LibBpf.divert_pkt_header(data);
                byte[] pktData = data.getByteArray(header.size(), header.packet_len);
                if (handle.packetQueue.size() < handle.maxQueueSize) {
                    handle.packetQueue.offer(new PacketEvent(pktData, header));
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
                lib.ring_buffer__poll(h.ringBuffer, 100);
                event = h.packetQueue.poll(100, TimeUnit.MILLISECONDS);
            }
            if (event != null) {
                buffer.getByteBuffer().put(event.data);
                address.Union.Network.IfIdx = event.header.ifindex;
                address.setOutbound(event.header.direction == 1); // 1 = Egress/Outbound in ebpfdivert
                return event.data.length;
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return 0;
    }

    @Override
    public <T> WinDivertAsyncResult<T> recvAsync(Handle handle, int bufsize, WinDivertAsyncResult.ResultConverter<T> converter) throws WinDivertException {
        throw new UnsupportedOperationException("Async recv not implemented for eBPF yet");
    }

    @Override
    public int send(Handle handle, ByteBuffer packet, WinDivertAddress address) throws WinDivertException {
        // TODO: Implement injection via AF_PACKET or bpf_redirect
        return packet.remaining();
    }

    @Override
    public <T> WinDivertAsyncResult<T> sendAsync(Handle handle, ByteBuffer packet, WinDivertAddress address, WinDivertAsyncResult.ResultConverter<T> converter) throws WinDivertException {
        throw new UnsupportedOperationException();
    }

    @Override
    public void shutdown(Handle handle, int how) throws WinDivertException {
        try {
            handle.close();
        } catch (Exception e) {
            throw new WinDivertException(-1, 
"Failed to shutdown handle", e);
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
