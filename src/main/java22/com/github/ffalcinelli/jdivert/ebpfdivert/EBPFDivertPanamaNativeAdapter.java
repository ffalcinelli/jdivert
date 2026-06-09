package com.github.ffalcinelli.jdivert.ebpfdivert;

import com.github.ffalcinelli.jdivert.WinDivertAsyncResult;
import com.github.ffalcinelli.jdivert.ebpfdivert.panama.LibBpfPanama;
import com.github.ffalcinelli.jdivert.exceptions.WinDivertException;
import com.github.ffalcinelli.jdivert.windivert.DeployHandler;
import com.github.ffalcinelli.jdivert.windivert.NativeAdapter;
import com.github.ffalcinelli.jdivert.windivert.WinDivertAddress;

import java.lang.foreign.*;
import java.lang.invoke.MethodHandle;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * eBPF implementation of NativeAdapter for Linux using Project Panama.
 * Targets Java 22+.
 */
public class EBPFDivertPanamaNativeAdapter implements NativeAdapter {

    private final Arena arena = Arena.ofShared();

    private static class EBPFHandle implements Handle {
        MemorySegment bpfObj;
        MemorySegment ringBuffer;
        int filterMapFd;
        BlockingQueue<PacketEvent> packetQueue = new LinkedBlockingQueue<>();
        Arena arena;

        @Override
        public void close() throws WinDivertException {
            try {
                if (ringBuffer != null && !ringBuffer.equals(MemorySegment.NULL)) {
                    LibBpfPanama.ring_buffer__free.invoke(ringBuffer);
                }
                if (bpfObj != null && !bpfObj.equals(MemorySegment.NULL)) {
                    LibBpfPanama.bpf_object__close.invoke(bpfObj);
                }
                if (arena != null) arena.close();
            } catch (Throwable t) {
                throw new WinDivertException(-1, "Failed to close BPF handle", t);
            }
        }

        @Override
        public boolean isValid() {
            return bpfObj != null && !bpfObj.equals(MemorySegment.NULL);
        }
    }

    private static class PanamaBuffer implements Buffer {
        private final MemorySegment segment;
        private final ByteBuffer byteBuffer;

        PanamaBuffer(int size) {
            this.segment = Arena.ofShared().allocate(size);
            this.byteBuffer = segment.asByteBuffer();
        }

        @Override
        public ByteBuffer getByteBuffer() {
            return byteBuffer;
        }

        @Override
        public int capacity() {
            return (int) segment.byteSize();
        }

        @Override
        public void close() {
        }
    }

    private static class PacketEvent {
        byte[] data;
        int ifindex;
        int direction;

        PacketEvent(byte[] data, int ifindex, int direction) {
            this.data = data;
            this.ifindex = ifindex;
            this.direction = direction;
        }
    }

    @Override
    public Handle open(String filter, int layer, short priority, long flags) throws WinDivertException {
        if (LibBpfPanama.bpf_object__open_file == null) {
            throw new WinDivertException(-1, "libbpf symbols not found");
        }
        try {
            Path bpfObjPath = DeployHandler.deployToPath();
            MemorySegment pathStr = arena.allocateFrom(bpfObjPath.toString());
            MemorySegment obj = (MemorySegment) LibBpfPanama.bpf_object__open_file.invoke(pathStr, MemorySegment.NULL);
            if (obj.equals(MemorySegment.NULL)) throw new WinDivertException(-1, "Failed to open BPF object");

            if ((int) LibBpfPanama.bpf_object__load.invoke(obj) != 0) {
                LibBpfPanama.bpf_object__close.invoke(obj);
                throw new WinDivertException(-1, 
"Failed to load BPF object");
            }

            EBPFHandle handle = new EBPFHandle();
            handle.bpfObj = obj;
            handle.arena = Arena.ofShared();

            // Attach programs
            MemorySegment ingressName = arena.allocateFrom("tc_ingress");
            MemorySegment progIngress = (MemorySegment) LibBpfPanama.bpf_object__find_program_by_name.invoke(obj, ingressName);
            if (!progIngress.equals(MemorySegment.NULL)) LibBpfPanama.bpf_program__attach.invoke(progIngress);
            
            MemorySegment egressName = arena.allocateFrom("tc_egress");
            MemorySegment progEgress = (MemorySegment) LibBpfPanama.bpf_object__find_program_by_name.invoke(obj, egressName);
            if (!progEgress.equals(MemorySegment.NULL)) LibBpfPanama.bpf_program__attach.invoke(progEgress);

            // Setup filter rules
            MemorySegment mapName = arena.allocateFrom("filter_rules");
            MemorySegment map = (MemorySegment) LibBpfPanama.bpf_object__find_map_by_name.invoke(obj, mapName);
            if (!map.equals(MemorySegment.NULL)) {
                handle.filterMapFd = (int) LibBpfPanama.bpf_map__fd.invoke(map);
                List<FilterTranspiler.BpfFilterRule> rules = FilterTranspiler.transpile(filter);
                for (int i = 0; i < rules.size(); i++) {
                    byte[] ruleBytes = rules.get(i).toBytes();
                    MemorySegment key = handle.arena.allocate(ValueLayout.JAVA_INT, i);
                    MemorySegment val = handle.arena.allocateFrom(ValueLayout.JAVA_BYTE, ruleBytes);
                    LibBpfPanama.bpf_map_update_elem.invoke(handle.filterMapFd, key, val, 0L);
                }
            }

            // Setup Ring Buffer
            MemorySegment rbMapName = arena.allocateFrom("pcap_ringbuf");
            MemorySegment rbMap = (MemorySegment) LibBpfPanama.bpf_object__find_map_by_name.invoke(obj, rbMapName);
            if (!rbMap.equals(MemorySegment.NULL)) {
                int rbFd = (int) LibBpfPanama.bpf_map__fd.invoke(rbMap);
                
                FunctionDescriptor cbDesc = FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_LONG);
                MemorySegment callback = Linker.nativeLinker().upcallStub(
                    java.lang.invoke.MethodHandles.lookup().findVirtual(EBPFHandle.class, "onSample", cbDesc.toMethodType())
                        .bindTo(handle),
                    cbDesc, handle.arena
                );

                handle.ringBuffer = (MemorySegment) LibBpfPanama.ring_buffer__new.invoke(rbFd, callback, MemorySegment.NULL, MemorySegment.NULL);
            }

            return handle;
        } catch (Throwable t) {
            throw new WinDivertException(-1, 
"Failed to open eBPF handle", t);
        }
    }

    // Callback for Ring Buffer
    @SuppressWarnings("unused")
    private int onSample(MemorySegment ctx, MemorySegment data, long size) {
        // divert_pkt_header: ifindex(4), direction(4), packet_len(4), l2_len(4)
        int ifindex = data.get(ValueLayout.JAVA_INT, 0);
        int direction = data.get(ValueLayout.JAVA_INT, 4);
        int packet_len = data.get(ValueLayout.JAVA_INT, 8);
        int header_size = 16;
        
        byte[] pktData = data.asSlice(header_size, packet_len).toArray(ValueLayout.JAVA_BYTE);
        ((EBPFHandle)currentHandle).packetQueue.offer(new PacketEvent(pktData, ifindex, direction));
        return 0;
    }

    private Handle currentHandle; // Simplified for single handle usage

    @Override
    public int recv(Handle handle, Buffer buffer, WinDivertAddress address) throws WinDivertException {
        EBPFHandle h = (EBPFHandle) handle;
        currentHandle = h;
        try {
            PacketEvent event = h.packetQueue.poll();
            if (event == null) {
                LibBpfPanama.ring_buffer__poll.invoke(h.ringBuffer, 100);
                event = h.packetQueue.poll(100, TimeUnit.MILLISECONDS);
            }
            if (event != null) {
                buffer.getByteBuffer().put(event.data);
                address.Union.Network.IfIdx = event.ifindex;
                address.setOutbound(event.direction == 1);
                return event.data.length;
            }
        } catch (Throwable t) {
            if (t instanceof InterruptedException) Thread.currentThread().interrupt();
            throw new WinDivertException(-1, 
"Recv failed", t);
        }
        return 0;
    }

    @Override
    public <T> WinDivertAsyncResult<T> recvAsync(Handle handle, int bufsize, WinDivertAsyncResult.ResultConverter<T> converter) throws WinDivertException {
        throw new UnsupportedOperationException();
    }

    @Override
    public int send(Handle handle, ByteBuffer packet, WinDivertAddress address) throws WinDivertException {
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
"Shutdown failed", e);
        }
    }

    @Override
    public void setParam(Handle handle, int param, long value) throws WinDivertException {
    }

    @Override
    public long getParam(Handle handle, int param) throws WinDivertException {
        return 0;
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
        return new PanamaBuffer(size);
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
