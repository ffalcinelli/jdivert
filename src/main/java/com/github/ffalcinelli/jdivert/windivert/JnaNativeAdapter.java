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
import com.sun.jna.Memory;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.Structure;
import com.sun.jna.Union;
import com.sun.jna.platform.win32.Kernel32;
import com.sun.jna.platform.win32.Kernel32Util;
import com.sun.jna.platform.win32.WinBase;
import com.sun.jna.platform.win32.WinNT;
import com.sun.jna.ptr.IntByReference;
import com.sun.jna.ptr.LongByReference;
import com.sun.jna.win32.W32APIOptions;

import java.util.Arrays;
import java.util.List;

/**
 * JNA implementation of NativeAdapter.
 */
public class JnaNativeAdapter implements NativeAdapter {

    private final WinDivertDLL dll = WinDivertDLL.INSTANCE;

    private interface MyKernel32 extends Kernel32 {
        MyKernel32 INSTANCE = Native.load("kernel32", MyKernel32.class, W32APIOptions.DEFAULT_OPTIONS);

        boolean GetOverlappedResult(HANDLE hFile, WinBase.OVERLAPPED lpOverlapped, IntByReference lpNumberOfBytesTransferred, boolean bWait);
    }

    @Override
    public Handle open(String filter, int layer, short priority, long flags) throws WinDivertException {
        WinNT.HANDLE handle = dll.WinDivertOpen(filter, layer, priority, flags);
        if (handle == WinBase.INVALID_HANDLE_VALUE) {
            WinDivertException.throwExceptionOnGetLastError();
        }
        return new JnaHandle(handle);
    }

    @Override
    public int recv(Handle handle, Buffer buffer, WinDivertAddress address) throws WinDivertException {
        JnaBuffer jnaBuffer = (JnaBuffer) buffer;
        JnaHandle jnaHandle = (JnaHandle) handle;
        IntByReference recvLen = new IntByReference();
        JnaWinDivertAddress jnaAddr = new JnaWinDivertAddress();

        if (!dll.WinDivertRecv(jnaHandle.handle, jnaBuffer.memory, jnaBuffer.capacity(), recvLen, jnaAddr.getPointer())) {
            WinDivertException.throwExceptionOnGetLastError();
        }
        jnaAddr.read();
        mapToPojo(jnaAddr, address);
        return recvLen.getValue();
    }

    @Override
    public <T> WinDivertAsyncResult<T> recvAsync(Handle handle, int bufsize, WinDivertAsyncResult.ResultConverter<T> converter) throws WinDivertException {
        JnaHandle jnaHandle = (JnaHandle) handle;
        JnaBuffer jnaBuffer = new JnaBuffer(bufsize);
        JnaWinDivertAddress jnaAddr = new JnaWinDivertAddress();
        WinDivertAddress address = new WinDivertAddress();

        WinBase.OVERLAPPED overlapped = new WinBase.OVERLAPPED();
        overlapped.hEvent = Kernel32.INSTANCE.CreateEvent(null, true, false, null);

        if (!dll.WinDivertRecvEx(jnaHandle.handle, jnaBuffer.memory, bufsize, null, 0, jnaAddr.getPointer(), null, overlapped)) {
            int err = Native.getLastError();
            if (err != WinNT.ERROR_IO_PENDING) {
                throw new WinDivertException(err);
            }
        }

        return new WinDivertAsyncResult<>(handle, jnaBuffer, address, converter, new JnaAsyncImplementation(jnaHandle, overlapped, jnaAddr, address));
    }

    @Override
    public int send(Handle handle, java.nio.ByteBuffer packet, WinDivertAddress address) throws WinDivertException {
        JnaHandle jnaHandle = (JnaHandle) handle;
        IntByReference sendLen = new IntByReference();
        
        Pointer memory;
        if (packet.isDirect()) {
            memory = Native.getDirectBufferPointer(packet);
        } else {
            Memory mem = new Memory(packet.remaining());
            mem.write(0, packet.array(), packet.arrayOffset() + packet.position(), packet.remaining());
            memory = mem;
        }
        
        JnaWinDivertAddress jnaAddr = new JnaWinDivertAddress();
        mapFromPojo(address, jnaAddr);
        jnaAddr.write();

        if (!dll.WinDivertSend(jnaHandle.handle, memory, packet.remaining(), sendLen, jnaAddr.getPointer())) {
            WinDivertException.throwExceptionOnGetLastError();
        }
        return sendLen.getValue();
    }

    @Override
    public <T> WinDivertAsyncResult<T> sendAsync(Handle handle, java.nio.ByteBuffer packet, WinDivertAddress address, WinDivertAsyncResult.ResultConverter<T> converter) throws WinDivertException {
        JnaHandle jnaHandle = (JnaHandle) handle;
        JnaBuffer jnaBuffer = new JnaBuffer(packet.remaining());
        java.nio.ByteBuffer dest = jnaBuffer.getByteBuffer();
        dest.put(packet.duplicate());
        
        JnaWinDivertAddress jnaAddr = new JnaWinDivertAddress();
        mapFromPojo(address, jnaAddr);
        jnaAddr.write();

        WinBase.OVERLAPPED overlapped = new WinBase.OVERLAPPED();
        overlapped.hEvent = Kernel32.INSTANCE.CreateEvent(null, true, false, null);

        if (!dll.WinDivertSendEx(jnaHandle.handle, jnaBuffer.memory, jnaBuffer.capacity(), null, 0, jnaAddr.getPointer(), jnaAddr.size(), overlapped)) {
            int err = Native.getLastError();
            if (err != WinNT.ERROR_IO_PENDING) {
                throw new WinDivertException(err);
            }
        }

        return new WinDivertAsyncResult<>(handle, jnaBuffer, address, converter, new JnaAsyncImplementation(jnaHandle, overlapped, jnaAddr, address));
    }

    @Override
    public void shutdown(Handle handle, int how) throws WinDivertException {
        if (!dll.WinDivertShutdown(((JnaHandle) handle).handle, how)) {
            WinDivertException.throwExceptionOnGetLastError();
        }
    }

    @Override
    public void setParam(Handle handle, int param, long value) throws WinDivertException {
        if (!dll.WinDivertSetParam(((JnaHandle) handle).handle, param, value)) {
            WinDivertException.throwExceptionOnGetLastError();
        }
    }

    @Override
    public long getParam(Handle handle, int param) throws WinDivertException {
        LongByReference pValue = new LongByReference();
        if (!dll.WinDivertGetParam(((JnaHandle) handle).handle, param, pValue)) {
            WinDivertException.throwExceptionOnGetLastError();
        }
        return pValue.getValue();
    }

    @Override
    public int calcChecksums(byte[] packet, WinDivertAddress address, long flags) {
        Memory memory = new Memory(packet.length);
        memory.write(0, packet, 0, packet.length);
        JnaWinDivertAddress jnaAddr = new JnaWinDivertAddress();
        mapFromPojo(address, jnaAddr);
        jnaAddr.write();

        int result = dll.WinDivertHelperCalcChecksums(memory, packet.length, jnaAddr.getPointer(), flags);
        byte[] updated = memory.getByteArray(0, packet.length);
        System.arraycopy(updated, 0, packet, 0, packet.length);
        return result;
    }

    @Override
    public long hashPacket(byte[] packet, long seed) {
        Memory memory = new Memory(packet.length);
        memory.write(0, packet, 0, packet.length);
        return dll.WinDivertHelperHashPacket(memory, packet.length, seed);
    }

    @Override
    public Buffer allocateBuffer(int size) {
        return new JnaBuffer(size);
    }

    @Override
    public String formatMessage(int errorCode) {
        return Kernel32Util.formatMessage(errorCode);
    }

    @Override
    public int getLastError() {
        return Native.getLastError();
    }

    private void mapToPojo(JnaWinDivertAddress jna, WinDivertAddress pojo) {
        pojo.Timestamp = jna.Timestamp;
        pojo.bitfield1 = jna.bitfield1;
        pojo.Reserved2 = jna.Reserved2;
        int layer = pojo.getLayer();
        switch (layer) {
            case 0:
            case 1:
                pojo.Union.Network.IfIdx = jna.Union.Network.IfIdx;
                pojo.Union.Network.SubIfIdx = jna.Union.Network.SubIfIdx;
                break;
            case 2:
                pojo.Union.Flow.EndpointId = jna.Union.Flow.EndpointId;
                pojo.Union.Flow.ParentEndpointId = jna.Union.Flow.ParentEndpointId;
                pojo.Union.Flow.ProcessId = jna.Union.Flow.ProcessId;
                System.arraycopy(jna.Union.Flow.LocalAddr, 0, pojo.Union.Flow.LocalAddr, 0, 4);
                System.arraycopy(jna.Union.Flow.RemoteAddr, 0, pojo.Union.Flow.RemoteAddr, 0, 4);
                pojo.Union.Flow.LocalPort = jna.Union.Flow.LocalPort;
                pojo.Union.Flow.RemotePort = jna.Union.Flow.RemotePort;
                pojo.Union.Flow.Protocol = jna.Union.Flow.Protocol;
                break;
            case 3:
                pojo.Union.Socket.EndpointId = jna.Union.Socket.EndpointId;
                pojo.Union.Socket.ParentEndpointId = jna.Union.Socket.ParentEndpointId;
                pojo.Union.Socket.ProcessId = jna.Union.Socket.ProcessId;
                System.arraycopy(jna.Union.Socket.LocalAddr, 0, pojo.Union.Socket.LocalAddr, 0, 4);
                System.arraycopy(jna.Union.Socket.RemoteAddr, 0, pojo.Union.Socket.RemoteAddr, 0, 4);
                pojo.Union.Socket.LocalPort = jna.Union.Socket.LocalPort;
                pojo.Union.Socket.RemotePort = jna.Union.Socket.RemotePort;
                pojo.Union.Socket.Protocol = jna.Union.Socket.Protocol;
                break;
            case 4:
                pojo.Union.Reflect.Timestamp = jna.Union.Reflect.Timestamp;
                pojo.Union.Reflect.ProcessId = jna.Union.Reflect.ProcessId;
                pojo.Union.Reflect.Layer = jna.Union.Reflect.Layer;
                pojo.Union.Reflect.Flags = jna.Union.Reflect.Flags;
                pojo.Union.Reflect.Priority = jna.Union.Reflect.Priority;
                break;
        }
    }

    private void mapFromPojo(WinDivertAddress pojo, JnaWinDivertAddress jna) {
        jna.Timestamp = pojo.Timestamp;
        jna.bitfield1 = pojo.bitfield1;
        jna.Reserved2 = pojo.Reserved2;
        int layer = pojo.getLayer();
        switch (layer) {
            case 0:
            case 1:
                jna.Union.setType(JnaWinDivertAddress.WinDivertData.NetworkData.class);
                jna.Union.Network.IfIdx = pojo.Union.Network.IfIdx;
                jna.Union.Network.SubIfIdx = pojo.Union.Network.SubIfIdx;
                break;
            case 2:
                jna.Union.setType(JnaWinDivertAddress.WinDivertData.FlowData.class);
                jna.Union.Flow.EndpointId = pojo.Union.Flow.EndpointId;
                jna.Union.Flow.ParentEndpointId = pojo.Union.Flow.ParentEndpointId;
                jna.Union.Flow.ProcessId = pojo.Union.Flow.ProcessId;
                System.arraycopy(pojo.Union.Flow.LocalAddr, 0, jna.Union.Flow.LocalAddr, 0, 4);
                System.arraycopy(pojo.Union.Flow.RemoteAddr, 0, jna.Union.Flow.RemoteAddr, 0, 4);
                jna.Union.Flow.LocalPort = pojo.Union.Flow.LocalPort;
                jna.Union.Flow.RemotePort = pojo.Union.Flow.RemotePort;
                jna.Union.Flow.Protocol = pojo.Union.Flow.Protocol;
                break;
            case 3:
                jna.Union.setType(JnaWinDivertAddress.WinDivertData.SocketData.class);
                jna.Union.Socket.EndpointId = pojo.Union.Socket.EndpointId;
                jna.Union.Socket.ParentEndpointId = pojo.Union.Socket.ParentEndpointId;
                jna.Union.Socket.ProcessId = pojo.Union.Socket.ProcessId;
                System.arraycopy(pojo.Union.Socket.LocalAddr, 0, jna.Union.Socket.LocalAddr, 0, 4);
                System.arraycopy(pojo.Union.Socket.RemoteAddr, 0, jna.Union.Socket.RemoteAddr, 0, 4);
                jna.Union.Socket.LocalPort = pojo.Union.Socket.LocalPort;
                jna.Union.Socket.RemotePort = pojo.Union.Socket.RemotePort;
                jna.Union.Socket.Protocol = pojo.Union.Socket.Protocol;
                break;
            case 4:
                jna.Union.setType(JnaWinDivertAddress.WinDivertData.ReflectData.class);
                jna.Union.Reflect.Timestamp = pojo.Union.Reflect.Timestamp;
                jna.Union.Reflect.ProcessId = pojo.Union.Reflect.ProcessId;
                jna.Union.Reflect.Layer = pojo.Union.Reflect.Layer;
                jna.Union.Reflect.Flags = pojo.Union.Reflect.Flags;
                jna.Union.Reflect.Priority = pojo.Union.Reflect.Priority;
                break;
        }
    }

    private static class JnaHandle implements Handle {
        final WinNT.HANDLE handle;

        JnaHandle(WinNT.HANDLE handle) {
            this.handle = handle;
        }

        @Override
        public void close() throws WinDivertException {
            if (!WinDivertDLL.INSTANCE.WinDivertClose(handle)) {
                WinDivertException.throwExceptionOnGetLastError();
            }
        }

        @Override
        public boolean isValid() {
            return handle != null && handle != WinBase.INVALID_HANDLE_VALUE;
        }
    }

    private static class JnaBuffer implements Buffer {
        final Memory memory;
        final int size;

        JnaBuffer(int size) {
            this.size = size;
            this.memory = new Memory(size);
        }

        @Override
        public java.nio.ByteBuffer getByteBuffer() {
            return memory.getByteBuffer(0, size);
        }

        @Override
        public int capacity() {
            return size;
        }

        @Override
        public void close() {
        }
    }

    private class JnaAsyncImplementation implements WinDivertAsyncResult.AsyncImplementation {
        private final JnaHandle handle;
        private final WinBase.OVERLAPPED overlapped;
        private final JnaWinDivertAddress jnaAddr;
        private final WinDivertAddress address;
        private static final int STATUS_PENDING = 0x103;

        JnaAsyncImplementation(JnaHandle handle, WinBase.OVERLAPPED overlapped, JnaWinDivertAddress jnaAddr, WinDivertAddress address) {
            this.handle = handle;
            this.overlapped = overlapped;
            this.jnaAddr = jnaAddr;
            this.address = address;
        }

        @Override
        public boolean isCompleted() {
            return overlapped.Internal.intValue() != STATUS_PENDING;
        }

        @Override
        public int waitAndGetResult() throws WinDivertException {
            IntByReference transferLen = new IntByReference();
            if (!MyKernel32.INSTANCE.GetOverlappedResult(handle.handle, overlapped, transferLen, true)) {
                WinDivertException.throwExceptionOnGetLastError();
            }
            if (jnaAddr != null) {
                jnaAddr.read();
                mapToPojo(jnaAddr, address);
            }
            // Clean up event handle
            if (overlapped.hEvent != null && overlapped.hEvent != WinNT.INVALID_HANDLE_VALUE) {
                Kernel32.INSTANCE.CloseHandle(overlapped.hEvent);
                overlapped.hEvent = null;
            }
            return transferLen.getValue();
        }
    }

    /**
     * Internal JNA structure matching WinDivertAddress layout.
     */
    public static class JnaWinDivertAddress extends Structure {
        public long Timestamp;
        public int bitfield1;
        public int Reserved2;
        public WinDivertData Union;

        public static class WinDivertData extends Union {
            public NetworkData Network;
            public FlowData Flow;
            public SocketData Socket;
            public ReflectData Reflect;
            public byte[] Reserved3 = new byte[64];

            public static class NetworkData extends Structure {
                public int IfIdx;
                public int SubIfIdx;

                @Override
                protected List<String> getFieldOrder() {
                    return Arrays.asList("IfIdx", "SubIfIdx");
                }
            }

            public static class FlowData extends Structure {
                public long EndpointId;
                public long ParentEndpointId;
                public int ProcessId;
                public int[] LocalAddr = new int[4];
                public int[] RemoteAddr = new int[4];
                public short LocalPort;
                public short RemotePort;
                public byte Protocol;

                @Override
                protected List<String> getFieldOrder() {
                    return Arrays.asList("EndpointId", "ParentEndpointId", "ProcessId", "LocalAddr", "RemoteAddr", "LocalPort", "RemotePort", "Protocol");
                }
            }

            public static class SocketData extends Structure {
                public long EndpointId;
                public long ParentEndpointId;
                public int ProcessId;
                public int[] LocalAddr = new int[4];
                public int[] RemoteAddr = new int[4];
                public short LocalPort;
                public short RemotePort;
                public byte Protocol;

                @Override
                protected List<String> getFieldOrder() {
                    return Arrays.asList("EndpointId", "ParentEndpointId", "ProcessId", "LocalAddr", "RemoteAddr", "LocalPort", "RemotePort", "Protocol");
                }
            }

            public static class ReflectData extends Structure {
                public long Timestamp;
                public int ProcessId;
                public int Layer;
                public long Flags;
                public short Priority;

                @Override
                protected List<String> getFieldOrder() {
                    return Arrays.asList("Timestamp", "ProcessId", "Layer", "Flags", "Priority");
                }
            }
        }

        public JnaWinDivertAddress() {
            Union = new WinDivertData();
        }

        @Override
        protected List<String> getFieldOrder() {
            return Arrays.asList("Timestamp", "bitfield1", "Reserved2", "Union");
        }

        @Override
        public void read() {
            super.read();
            int layer = bitfield1 & 0xFF;
            switch (layer) {
                case 0:
                case 1:
                    Union.setType(WinDivertData.NetworkData.class);
                    break;
                case 2:
                    Union.setType(WinDivertData.FlowData.class);
                    break;
                case 3:
                    Union.setType(WinDivertData.SocketData.class);
                    break;
                case 4:
                    Union.setType(WinDivertData.ReflectData.class);
                    break;
                default:
                    Union.setType(byte[].class);
            }
            Union.read();
        }
    }
}
