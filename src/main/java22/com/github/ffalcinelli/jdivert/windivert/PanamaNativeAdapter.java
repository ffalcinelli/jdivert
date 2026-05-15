/*
 * Copyright (c) Fabio Falcinelli 2016.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.github.ffalcinelli.jdivert.windivert;

import com.github.ffalcinelli.jdivert.WinDivertAsyncResult;
import com.github.ffalcinelli.jdivert.exceptions.WinDivertException;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.StructLayout;
import java.lang.foreign.SymbolLookup;
import java.lang.invoke.MethodHandle;
import java.nio.file.Path;
import java.util.Objects;

import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_BOOLEAN;
import static java.lang.foreign.ValueLayout.JAVA_BYTE;
import static java.lang.foreign.ValueLayout.JAVA_CHAR;
import static java.lang.foreign.ValueLayout.JAVA_INT;
import static java.lang.foreign.ValueLayout.JAVA_LONG;
import static java.lang.foreign.ValueLayout.JAVA_SHORT;

/**
 * Project Panama (FFM API) implementation of NativeAdapter.
 * Targets Java 22+.
 */
public class PanamaNativeAdapter implements NativeAdapter {

    public static final int ERROR_IO_PENDING = 997;
    public static final int FORMAT_MESSAGE_FROM_SYSTEM = 0x00001000;
    public static final int DEFAULT_BUFFER_SIZE = 1024;
    public static final long OVERLAPPED_ADDRESS = 0x103L;
    private static final SymbolLookup LOOKUP;
    private static final Linker LINKER = Linker.nativeLinker();
    // Native Function Handles
    private static final MethodHandle WinDivertOpen = link("WinDivertOpen", FunctionDescriptor.of(JAVA_LONG, ADDRESS, JAVA_INT, JAVA_SHORT, JAVA_LONG));
    private static final MethodHandle WinDivertClose = link("WinDivertClose", FunctionDescriptor.of(JAVA_BOOLEAN, JAVA_LONG));
    private static final MethodHandle WinDivertRecv = link("WinDivertRecv", FunctionDescriptor.of(JAVA_BOOLEAN, JAVA_LONG, ADDRESS, JAVA_INT, ADDRESS, ADDRESS));
    private static final MethodHandle WinDivertRecvEx = link("WinDivertRecvEx", FunctionDescriptor.of(JAVA_BOOLEAN, JAVA_LONG, ADDRESS, JAVA_INT, ADDRESS, JAVA_LONG, ADDRESS, ADDRESS, ADDRESS));
    private static final MethodHandle WinDivertSend = link("WinDivertSend", FunctionDescriptor.of(JAVA_BOOLEAN, JAVA_LONG, ADDRESS, JAVA_INT, ADDRESS, ADDRESS));
    private static final MethodHandle WinDivertSendEx = link("WinDivertSendEx", FunctionDescriptor.of(JAVA_BOOLEAN, JAVA_LONG, ADDRESS, JAVA_INT, ADDRESS, JAVA_LONG, ADDRESS, JAVA_INT, ADDRESS));
    private static final MethodHandle WinDivertShutdown = link("WinDivertShutdown", FunctionDescriptor.of(JAVA_BOOLEAN, JAVA_LONG, JAVA_INT));
    private static final MethodHandle WinDivertSetParam = link("WinDivertSetParam", FunctionDescriptor.of(JAVA_BOOLEAN, JAVA_LONG, JAVA_INT, JAVA_LONG));
    private static final MethodHandle WinDivertGetParam = link("WinDivertGetParam", FunctionDescriptor.of(JAVA_BOOLEAN, JAVA_LONG, JAVA_INT, ADDRESS));
    private static final MethodHandle WinDivertHelperCalcChecksums = link("WinDivertHelperCalcChecksums", FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_INT, ADDRESS, JAVA_LONG));
    private static final MethodHandle WinDivertHelperHashPacket = link("WinDivertHelperHashPacket", FunctionDescriptor.of(JAVA_LONG, ADDRESS, JAVA_INT, JAVA_LONG));
    // Kernel32 Function Handles
    private static final SymbolLookup KERNEL32_LOOKUP = SymbolLookup.libraryLookup("kernel32", Arena.global());
    private static final MethodHandle GetLastError = link(KERNEL32_LOOKUP, "GetLastError", FunctionDescriptor.of(JAVA_INT));
    private static final MethodHandle FormatMessageW = link(KERNEL32_LOOKUP, "FormatMessageW", FunctionDescriptor.of(JAVA_INT, JAVA_INT, ADDRESS, JAVA_INT, JAVA_INT, ADDRESS, JAVA_INT, ADDRESS));
    private static final MethodHandle CreateEventW = link(KERNEL32_LOOKUP, "CreateEventW", FunctionDescriptor.of(ADDRESS, ADDRESS, JAVA_BOOLEAN, JAVA_BOOLEAN, ADDRESS));
    private static final MethodHandle CloseHandle = link(KERNEL32_LOOKUP, "CloseHandle", FunctionDescriptor.of(JAVA_BOOLEAN, ADDRESS));
    private static final MethodHandle GetOverlappedResult = link(KERNEL32_LOOKUP, "GetOverlappedResult", FunctionDescriptor.of(JAVA_BOOLEAN, ADDRESS, ADDRESS, ADDRESS, JAVA_BOOLEAN));
    // Memory Layouts
    private static final StructLayout OVERLAPPED_LAYOUT = MemoryLayout.structLayout(
            ADDRESS.withName("Internal"),
            ADDRESS.withName("InternalHigh"),
            JAVA_INT.withName("Offset"),
            JAVA_INT.withName("OffsetHigh"),
            ADDRESS.withName("hEvent")
    );
    private static final StructLayout ADDRESS_LAYOUT = MemoryLayout.structLayout(
            JAVA_LONG.withName("Timestamp"),
            JAVA_INT.withName("bitfield1"),
            JAVA_INT.withName("Reserved2"),
            MemoryLayout.unionLayout(
                    MemoryLayout.structLayout(JAVA_INT.withName("IfIdx"), JAVA_INT.withName("SubIfIdx")).withName("Network"),
                    MemoryLayout.structLayout(
                            JAVA_LONG.withName("EndpointId"),
                            JAVA_LONG.withName("ParentEndpointId"),
                            JAVA_INT.withName("ProcessId"),
                            MemoryLayout.sequenceLayout(4, JAVA_INT).withName("LocalAddr"),
                            MemoryLayout.sequenceLayout(4, JAVA_INT).withName("RemoteAddr"),
                            JAVA_SHORT.withName("LocalPort"),
                            JAVA_SHORT.withName("RemotePort"),
                            JAVA_BYTE.withName("Protocol"),
                            MemoryLayout.paddingLayout(7)
                    ).withName("Flow"),
                    MemoryLayout.structLayout(
                            JAVA_LONG.withName("EndpointId"),
                            JAVA_LONG.withName("ParentEndpointId"),
                            JAVA_INT.withName("ProcessId"),
                            MemoryLayout.sequenceLayout(4, JAVA_INT).withName("LocalAddr"),
                            MemoryLayout.sequenceLayout(4, JAVA_INT).withName("RemoteAddr"),
                            JAVA_SHORT.withName("LocalPort"),
                            JAVA_SHORT.withName("RemotePort"),
                            JAVA_BYTE.withName("Protocol"),
                            MemoryLayout.paddingLayout(7)
                    ).withName("Socket"),
                    MemoryLayout.structLayout(
                            JAVA_LONG.withName("Timestamp"),
                            JAVA_INT.withName("ProcessId"),
                            JAVA_INT.withName("Layer"),
                            JAVA_LONG.withName("Flags"),
                            JAVA_SHORT.withName("Priority"),
                            MemoryLayout.paddingLayout(6)
                    ).withName("Reflect"),
                    MemoryLayout.sequenceLayout(64, JAVA_BYTE).withName("Reserved3")
            ).withName("Union")
    );

    static {
        // Deploy binaries first
        Path dllPath = DeployHandler.deployToPath();
        System.load(dllPath.toAbsolutePath().toString());
        LOOKUP = SymbolLookup.loaderLookup();
    }

    private static MethodHandle link(String name, FunctionDescriptor desc) {
        return link(LOOKUP, name, desc);
    }

    private static MethodHandle link(SymbolLookup lookup, String name, FunctionDescriptor desc) {
        return lookup.find(name).map(s -> LINKER.downcallHandle(s, desc)).orElseThrow(() -> new UnsatisfiedLinkError(name));
    }

    @Override
    public Handle open(String filter, int layer, short priority, long flags) throws WinDivertException {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment cFilter = arena.allocateFrom(filter);
            long handle = (long) WinDivertOpen.invokeExact(cFilter, layer, priority, flags);
            if (handle == -1L) {
                WinDivertException.throwExceptionOnGetLastError();
            }
            return new PanamaHandle(handle);
        } catch (Throwable t) {
            if (t instanceof WinDivertException) throw (WinDivertException) t;
            throw new RuntimeException(t);
        }
    }

    @Override
    public int recv(Handle handle, Buffer buffer, WinDivertAddress address) throws WinDivertException {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment pRecvLen = arena.allocate(JAVA_INT);
            MemorySegment pAddr = arena.allocate(ADDRESS_LAYOUT);
            PanamaBuffer pBuf = (PanamaBuffer) buffer;
            PanamaHandle pHandle = (PanamaHandle) handle;

            boolean result = (boolean) WinDivertRecv.invokeExact(pHandle.handle, pBuf.segment, pBuf.capacity(), pRecvLen, pAddr);
            if (!result) {
                WinDivertException.throwExceptionOnGetLastError();
            }
            mapToPojo(pAddr, address);
            return pRecvLen.get(JAVA_INT, 0);
        } catch (Throwable t) {
            if (t instanceof WinDivertException) throw (WinDivertException) t;
            throw new RuntimeException(t);
        }
    }

    @Override
    public <T> WinDivertAsyncResult<T> recvAsync(Handle handle, int bufsize, WinDivertAsyncResult.ResultConverter<T> converter) throws WinDivertException {
        Arena arena = Arena.ofShared();
        MemorySegment pAddr = arena.allocate(ADDRESS_LAYOUT);
        PanamaBuffer pBuf = new PanamaBuffer(bufsize, arena);
        PanamaHandle pHandle = (PanamaHandle) handle;
        WinDivertAddress address = new WinDivertAddress();

        try {
            MemorySegment hEvent = (MemorySegment) CreateEventW.invokeExact(MemorySegment.NULL, true, false, MemorySegment.NULL);
            MemorySegment pOverlapped = arena.allocate(OVERLAPPED_LAYOUT);
            pOverlapped.set(ADDRESS, OVERLAPPED_LAYOUT.byteOffset(MemoryLayout.PathElement.groupElement("hEvent")), hEvent);

            boolean result = (boolean) WinDivertRecvEx.invokeExact(pHandle.handle, pBuf.segment, bufsize, MemorySegment.NULL, 0L, pAddr, MemorySegment.NULL, pOverlapped, MemorySegment.NULL);
            if (!result) {
                int err = (int) GetLastError.invokeExact();
                if (err != ERROR_IO_PENDING) {
                    arena.close();
                    throw new WinDivertException(err);
                }
            }
            return new WinDivertAsyncResult<>(handle, pBuf, address, converter, new PanamaAsyncImplementation(pHandle, pOverlapped, pAddr, address, arena));
        } catch (Throwable t) {
            arena.close();
            if (t instanceof WinDivertException) throw (WinDivertException) t;
            throw new RuntimeException(t);
        }
    }

    @Override
    public int send(Handle handle, java.nio.ByteBuffer packet, WinDivertAddress address) throws WinDivertException {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment pSendLen = arena.allocate(JAVA_INT);
            MemorySegment pAddr = arena.allocate(ADDRESS_LAYOUT);
            mapFromPojo(address, pAddr);
            MemorySegment pPacket = MemorySegment.ofBuffer(packet);
            PanamaHandle pHandle = (PanamaHandle) handle;

            boolean result = (boolean) WinDivertSend.invokeExact(pHandle.handle, pPacket, (int) pPacket.byteSize(), pSendLen, pAddr);
            if (!result) {
                WinDivertException.throwExceptionOnGetLastError();
            }
            return pSendLen.get(JAVA_INT, 0);
        } catch (Throwable t) {
            if (t instanceof WinDivertException) throw (WinDivertException) t;
            throw new RuntimeException(t);
        }
    }

    @Override
    public <T> WinDivertAsyncResult<T> sendAsync(Handle handle, java.nio.ByteBuffer packet, WinDivertAddress address, WinDivertAsyncResult.ResultConverter<T> converter) throws WinDivertException {
        Arena arena = Arena.ofShared();
        MemorySegment pAddr = arena.allocate(ADDRESS_LAYOUT);
        mapFromPojo(address, pAddr);
        PanamaBuffer pBuf = new PanamaBuffer(packet.remaining(), arena);
        pBuf.getByteBuffer().put(packet.duplicate());
        PanamaHandle pHandle = (PanamaHandle) handle;

        try {
            MemorySegment hEvent = (MemorySegment) CreateEventW.invokeExact(MemorySegment.NULL, true, false, MemorySegment.NULL);
            MemorySegment pOverlapped = arena.allocate(OVERLAPPED_LAYOUT);
            pOverlapped.set(ADDRESS, OVERLAPPED_LAYOUT.byteOffset(MemoryLayout.PathElement.groupElement("hEvent")), hEvent);

            boolean result = (boolean) WinDivertSendEx.invokeExact(pHandle.handle, pBuf.segment, (int) pBuf.segment.byteSize(), MemorySegment.NULL, 0L, pAddr, (int) ADDRESS_LAYOUT.byteSize(), pOverlapped);
            if (!result) {
                int err = (int) GetLastError.invokeExact();
                if (err != ERROR_IO_PENDING) {
                    arena.close();
                    throw new WinDivertException(err);
                }
            }
            return new WinDivertAsyncResult<>(handle, pBuf, address, converter, new PanamaAsyncImplementation(pHandle, pOverlapped, MemorySegment.NULL, address, arena));
        } catch (Throwable t) {
            arena.close();
            if (t instanceof WinDivertException) throw (WinDivertException) t;
            throw new RuntimeException(t);
        }
    }

    @Override
    public void shutdown(Handle handle, int how) throws WinDivertException {
        try {
            if (!(boolean) WinDivertShutdown.invokeExact(((PanamaHandle) handle).handle, how)) {
                WinDivertException.throwExceptionOnGetLastError();
            }
        } catch (Throwable t) {
            if (t instanceof WinDivertException) throw (WinDivertException) t;
            throw new RuntimeException(t);
        }
    }

    @Override
    public void setParam(Handle handle, int param, long value) throws WinDivertException {
        try {
            if (!(boolean) WinDivertSetParam.invokeExact(((PanamaHandle) handle).handle, param, value)) {
                WinDivertException.throwExceptionOnGetLastError();
            }
        } catch (Throwable t) {
            if (t instanceof WinDivertException) throw (WinDivertException) t;
            throw new RuntimeException(t);
        }
    }

    @Override
    public long getParam(Handle handle, int param) throws WinDivertException {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment pValue = arena.allocate(JAVA_LONG);
            if (!(boolean) WinDivertGetParam.invokeExact(((PanamaHandle) handle).handle, param, pValue)) {
                WinDivertException.throwExceptionOnGetLastError();
            }
            return pValue.get(JAVA_LONG, 0);
        } catch (Throwable t) {
            if (t instanceof WinDivertException) throw (WinDivertException) t;
            throw new RuntimeException(t);
        }
    }

    @Override
    public int calcChecksums(byte[] packet, WinDivertAddress address, long flags) {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment pPacket = arena.allocateFrom(JAVA_BYTE, packet);
            MemorySegment pAddr = arena.allocate(ADDRESS_LAYOUT);
            mapFromPojo(address, pAddr);
            int result = (int) WinDivertHelperCalcChecksums.invokeExact(pPacket, packet.length, pAddr, flags);
            MemorySegment.copy(pPacket, 0, MemorySegment.ofArray(packet), 0, packet.length);
            return result;
        } catch (Throwable t) {
            throw new RuntimeException(t);
        }
    }

    @Override
    public long hashPacket(byte[] packet, long seed) {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment pPacket = arena.allocateFrom(JAVA_BYTE, packet);
            return (long) WinDivertHelperHashPacket.invokeExact(pPacket, packet.length, seed);
        } catch (Throwable t) {
            throw new RuntimeException(t);
        }
    }

    @Override
    public Buffer allocateBuffer(int size) {
        return new PanamaBuffer(size, Arena.ofAuto());
    }

    @Override
    public String formatMessage(int errorCode) {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment buffer = arena.allocate(JAVA_CHAR, DEFAULT_BUFFER_SIZE);
            int len = (int) FormatMessageW.invokeExact(FORMAT_MESSAGE_FROM_SYSTEM, 0, errorCode, 0, buffer, DEFAULT_BUFFER_SIZE, MemorySegment.NULL);
            if (len > 0) {
                return buffer.getString(0, java.nio.charset.StandardCharsets.UTF_16LE).trim();
            }
            return "Unknown error: " + errorCode;
        } catch (Throwable t) {
            return "Error formatting message: " + t.getMessage();
        }
    }

    @Override
    public int getLastError() {
        try {
            return (int) GetLastError.invokeExact();
        } catch (Throwable t) {
            return -1;
        }
    }

    private void mapToPojo(MemorySegment seg, WinDivertAddress pojo) {
        pojo.Timestamp = seg.get(JAVA_LONG, ADDRESS_LAYOUT.byteOffset(MemoryLayout.PathElement.groupElement("Timestamp")));
        pojo.bitfield1 = seg.get(JAVA_INT, ADDRESS_LAYOUT.byteOffset(MemoryLayout.PathElement.groupElement("bitfield1")));
        pojo.Reserved2 = seg.get(JAVA_INT, ADDRESS_LAYOUT.byteOffset(MemoryLayout.PathElement.groupElement("Reserved2")));
        int layer = pojo.getLayer();
        long unionOffset = ADDRESS_LAYOUT.byteOffset(MemoryLayout.PathElement.groupElement("Union"));
        switch (layer) {
            case 0:
            case 1:
                pojo.Union.Network.IfIdx = seg.get(JAVA_INT, unionOffset);
                pojo.Union.Network.SubIfIdx = seg.get(JAVA_INT, unionOffset + 4);
                break;
            case 2:
                pojo.Union.Flow.EndpointId = seg.get(JAVA_LONG, unionOffset);
                pojo.Union.Flow.ParentEndpointId = seg.get(JAVA_LONG, unionOffset + 8);
                pojo.Union.Flow.ProcessId = seg.get(JAVA_INT, unionOffset + 16);
                MemorySegment.copy(seg, unionOffset + 20, MemorySegment.ofArray(pojo.Union.Flow.LocalAddr), 0, 16);
                MemorySegment.copy(seg, unionOffset + 36, MemorySegment.ofArray(pojo.Union.Flow.RemoteAddr), 0, 16);
                pojo.Union.Flow.LocalPort = seg.get(JAVA_SHORT, unionOffset + 52);
                pojo.Union.Flow.RemotePort = seg.get(JAVA_SHORT, unionOffset + 54);
                pojo.Union.Flow.Protocol = seg.get(JAVA_BYTE, unionOffset + 56);
                break;
            case 3:
                pojo.Union.Socket.EndpointId = seg.get(JAVA_LONG, unionOffset);
                pojo.Union.Socket.ParentEndpointId = seg.get(JAVA_LONG, unionOffset + 8);
                pojo.Union.Socket.ProcessId = seg.get(JAVA_INT, unionOffset + 16);
                MemorySegment.copy(seg, unionOffset + 20, MemorySegment.ofArray(pojo.Union.Socket.LocalAddr), 0, 16);
                MemorySegment.copy(seg, unionOffset + 36, MemorySegment.ofArray(pojo.Union.Socket.RemoteAddr), 0, 16);
                pojo.Union.Socket.LocalPort = seg.get(JAVA_SHORT, unionOffset + 52);
                pojo.Union.Socket.RemotePort = seg.get(JAVA_SHORT, unionOffset + 54);
                pojo.Union.Socket.Protocol = seg.get(JAVA_BYTE, unionOffset + 56);
                break;
            case 4:
                pojo.Union.Reflect.Timestamp = seg.get(JAVA_LONG, unionOffset);
                pojo.Union.Reflect.ProcessId = seg.get(JAVA_INT, unionOffset + 8);
                pojo.Union.Reflect.Layer = seg.get(JAVA_INT, unionOffset + 12);
                pojo.Union.Reflect.Flags = seg.get(JAVA_LONG, unionOffset + 16);
                pojo.Union.Reflect.Priority = seg.get(JAVA_SHORT, unionOffset + 24);
                break;
        }
    }

    private void mapFromPojo(WinDivertAddress pojo, MemorySegment seg) {
        seg.set(JAVA_LONG, ADDRESS_LAYOUT.byteOffset(MemoryLayout.PathElement.groupElement("Timestamp")), pojo.Timestamp);
        seg.set(JAVA_INT, ADDRESS_LAYOUT.byteOffset(MemoryLayout.PathElement.groupElement("bitfield1")), pojo.bitfield1);
        seg.set(JAVA_INT, ADDRESS_LAYOUT.byteOffset(MemoryLayout.PathElement.groupElement("Reserved2")), pojo.Reserved2);
        int layer = pojo.getLayer();
        long unionOffset = ADDRESS_LAYOUT.byteOffset(MemoryLayout.PathElement.groupElement("Union"));
        switch (layer) {
            case 0:
            case 1:
                seg.set(JAVA_INT, unionOffset, pojo.Union.Network.IfIdx);
                seg.set(JAVA_INT, unionOffset + 4, pojo.Union.Network.SubIfIdx);
                break;
            case 2:
                seg.set(JAVA_LONG, unionOffset, pojo.Union.Flow.EndpointId);
                seg.set(JAVA_LONG, unionOffset + 8, pojo.Union.Flow.ParentEndpointId);
                seg.set(JAVA_INT, unionOffset + 16, pojo.Union.Flow.ProcessId);
                MemorySegment.copy(MemorySegment.ofArray(pojo.Union.Flow.LocalAddr), 0, seg, unionOffset + 20, 16);
                MemorySegment.copy(MemorySegment.ofArray(pojo.Union.Flow.RemoteAddr), 0, seg, unionOffset + 36, 16);
                seg.set(JAVA_SHORT, unionOffset + 52, pojo.Union.Flow.LocalPort);
                seg.set(JAVA_SHORT, unionOffset + 54, pojo.Union.Flow.RemotePort);
                seg.set(JAVA_BYTE, unionOffset + 56, pojo.Union.Flow.Protocol);
                break;
            case 3:
                seg.set(JAVA_LONG, unionOffset, pojo.Union.Socket.EndpointId);
                seg.set(JAVA_LONG, unionOffset + 8, pojo.Union.Socket.ParentEndpointId);
                seg.set(JAVA_INT, unionOffset + 16, pojo.Union.Socket.ProcessId);
                MemorySegment.copy(MemorySegment.ofArray(pojo.Union.Socket.LocalAddr), 0, seg, unionOffset + 20, 16);
                MemorySegment.copy(MemorySegment.ofArray(pojo.Union.Socket.RemoteAddr), 0, seg, unionOffset + 36, 16);
                seg.set(JAVA_SHORT, unionOffset + 52, pojo.Union.Socket.LocalPort);
                seg.set(JAVA_SHORT, unionOffset + 54, pojo.Union.Socket.RemotePort);
                seg.set(JAVA_BYTE, unionOffset + 56, pojo.Union.Socket.Protocol);
                break;
            case 4:
                seg.set(JAVA_LONG, unionOffset, pojo.Union.Reflect.Timestamp);
                seg.set(JAVA_INT, unionOffset + 8, pojo.Union.Reflect.ProcessId);
                seg.set(JAVA_INT, unionOffset + 12, pojo.Union.Reflect.Layer);
                seg.set(JAVA_LONG, unionOffset + 16, pojo.Union.Reflect.Flags);
                seg.set(JAVA_SHORT, unionOffset + 24, pojo.Union.Reflect.Priority);
                break;
        }
    }

    private record PanamaHandle(long handle) implements Handle {

        @Override
        public void close() throws WinDivertException {
            try {
                if (!(boolean) WinDivertClose.invokeExact(handle)) {
                    WinDivertException.throwExceptionOnGetLastError();
                }
            } catch (Throwable t) {
                if (t instanceof WinDivertException) throw (WinDivertException) t;
                throw new RuntimeException(t);
            }
        }

        @Override
        public boolean isValid() {
            return handle != -1L;
        }
    }

    private static class PanamaBuffer implements Buffer {
        final MemorySegment segment;
        final int size;
        final Arena arena;

        PanamaBuffer(int size, Arena arena) {
            this.size = size;
            this.arena = arena;
            this.segment = arena.allocate(JAVA_BYTE, size);
        }

        @Override
        public java.nio.ByteBuffer getByteBuffer() {
            return segment.asByteBuffer();
        }

        @Override
        public int capacity() {
            return size;
        }

        @Override
        public void close() {
            try {
                if (arena.scope().isAlive()) {
                    arena.close();
                }
            } catch (UnsupportedOperationException ignored) {
            }
        }
    }

    private class PanamaAsyncImplementation implements WinDivertAsyncResult.AsyncImplementation {
        private final PanamaHandle handle;
        private final MemorySegment pOverlapped;
        private final MemorySegment pAddr;
        private final WinDivertAddress pojoAddr;
        private final Arena arena;

        PanamaAsyncImplementation(PanamaHandle handle, MemorySegment pOverlapped, MemorySegment pAddr, WinDivertAddress pojoAddr, Arena arena) {
            this.handle = handle;
            this.pOverlapped = pOverlapped;
            this.pAddr = pAddr;
            this.pojoAddr = pojoAddr;
            this.arena = arena;
        }

        @Override
        public boolean isCompleted() {
            // Check Internal field of OVERLAPPED (offset 0)
            return !Objects.equals(pOverlapped.get(ADDRESS, 0), MemorySegment.ofAddress(OVERLAPPED_ADDRESS));
        }

        @Override
        public int waitAndGetResult() throws WinDivertException {
            try (Arena confined = Arena.ofConfined()) {
                MemorySegment pTransferLen = confined.allocate(JAVA_INT);
                boolean result = (boolean) GetOverlappedResult.invokeExact(MemorySegment.ofAddress(handle.handle), pOverlapped, pTransferLen, true);
                if (!result) {
                    WinDivertException.throwExceptionOnGetLastError();
                }
                if (pAddr != MemorySegment.NULL) {
                    mapToPojo(pAddr, pojoAddr);
                }
                // Close hEvent
                MemorySegment hEvent = pOverlapped.get(ADDRESS, OVERLAPPED_LAYOUT.byteOffset(MemoryLayout.PathElement.groupElement("hEvent")));
                if (hEvent != MemorySegment.NULL) {
                    CloseHandle.invokeExact(hEvent);
                }
                return pTransferLen.get(JAVA_INT, 0);
            } catch (Throwable t) {
                if (t instanceof WinDivertException) throw (WinDivertException) t;
                throw new RuntimeException(t);
            } finally {
                arena.close();
            }
        }
    }
}
