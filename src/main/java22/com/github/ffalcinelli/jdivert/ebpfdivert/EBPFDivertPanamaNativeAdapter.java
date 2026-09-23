/*
 * Copyright (c) Fabio Falcinelli 2026.
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

package com.github.ffalcinelli.jdivert.ebpfdivert;

import com.github.ffalcinelli.jdivert.WinDivertAsyncResult;
import com.github.ffalcinelli.jdivert.exceptions.WinDivertException;
import com.github.ffalcinelli.jdivert.windivert.AddressCodec;
import com.github.ffalcinelli.jdivert.windivert.DeployHandler;
import com.github.ffalcinelli.jdivert.windivert.NativeAdapter;
import com.github.ffalcinelli.jdivert.windivert.WinDivertAddress;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.invoke.MethodHandle;
import java.nio.ByteBuffer;
import java.util.Objects;

import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_BYTE;
import static java.lang.foreign.ValueLayout.JAVA_INT;
import static java.lang.foreign.ValueLayout.JAVA_LONG;
import static java.lang.foreign.ValueLayout.JAVA_SHORT;

/**
 * Linux {@link NativeAdapter} over libebpfdivert, through the Foreign Function &amp; Memory API (Java 22+).
 */
public class EBPFDivertPanamaNativeAdapter implements NativeAdapter {

    private static final Linker LINKER = Linker.nativeLinker();
    private static final SymbolLookup LOOKUP =
            SymbolLookup.libraryLookup(DeployHandler.deployToPath(), Arena.global());

    private static final MethodHandle OPEN_EX = handle("ebpfdivert_open_ex",
            FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_INT, JAVA_SHORT, JAVA_LONG, ADDRESS, ADDRESS));
    private static final MethodHandle RECV = handle("ebpfdivert_recv",
            FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS, JAVA_INT, ADDRESS, ADDRESS, JAVA_INT));
    private static final MethodHandle SEND = handle("ebpfdivert_send",
            FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS, JAVA_INT, ADDRESS, ADDRESS));
    private static final MethodHandle SHUTDOWN = handle("ebpfdivert_shutdown",
            FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_INT));
    private static final MethodHandle CLOSE = handle("ebpfdivert_close",
            FunctionDescriptor.of(JAVA_INT, ADDRESS));
    private static final MethodHandle SET_PARAM = handle("ebpfdivert_set_param",
            FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_INT, JAVA_LONG));
    private static final MethodHandle GET_PARAM = handle("ebpfdivert_get_param",
            FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_INT, ADDRESS));
    private static final MethodHandle UNREGISTER = handle("ebpfdivert_unregister",
            FunctionDescriptor.of(JAVA_INT));
    private static final MethodHandle STRERROR = handle("ebpfdivert_strerror",
            FunctionDescriptor.of(ADDRESS, JAVA_INT));
    private static final MethodHandle COMPILE = handle("ebpfdivert_helper_compile_filter",
            FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_INT, ADDRESS, ADDRESS));
    private static final MethodHandle CALC_CHECKSUMS = handle("ebpfdivert_helper_calc_checksums",
            FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_INT, ADDRESS, JAVA_LONG));
    private static final MethodHandle HASH = handle("ebpfdivert_helper_hash_packet",
            FunctionDescriptor.of(JAVA_LONG, ADDRESS, JAVA_INT, JAVA_LONG));

    private static MethodHandle handle(String name, FunctionDescriptor descriptor) {
        return LINKER.downcallHandle(LOOKUP.find(name).orElseThrow(
                () -> new UnsatisfiedLinkError("libebpfdivert: missing " + name)), descriptor);
    }

    private static String cString(MemorySegment p) {
        return p.equals(MemorySegment.NULL) ? null : p.reinterpret(Long.MAX_VALUE).getString(0);
    }

    private static WinDivertException error(int rc, String op) {
        String msg;
        try {
            msg = cString((MemorySegment) STRERROR.invokeExact(rc));
        } catch (Throwable t) {
            msg = null;
        }
        return EBPFDivertSupport.error(rc, op, msg);
    }

    private static WinDivertException wrap(Throwable t) {
        if (t instanceof WinDivertException) {
            return (WinDivertException) t;
        }
        return new WinDivertException(-1, "libebpfdivert call failed", t);
    }

    @Override
    public Handle open(String filter, int layer, short priority, long flags) throws WinDivertException {
        try (Arena arena = Arena.ofConfined()) {
            String[] ifnames = EBPFDivertSupport.interfaces();
            MemorySegment names = MemorySegment.NULL;
            if (ifnames != null) {
                names = arena.allocate(ADDRESS, ifnames.length + 1);
                for (int i = 0; i < ifnames.length; i++) {
                    names.setAtIndex(ADDRESS, i, arena.allocateFrom(ifnames[i]));
                }
                names.setAtIndex(ADDRESS, ifnames.length, MemorySegment.NULL);
            }
            // struct ebpfdivert_open_opts { size_t sz; const char *const *ifnames; uint32_t ring_bytes; }
            MemorySegment opts = arena.allocate(24, 8);
            opts.set(JAVA_LONG, 0, 24);
            opts.set(ADDRESS, 8, names);
            opts.set(JAVA_INT, 16, EBPFDivertSupport.ringBytes());
            MemorySegment out = arena.allocate(ADDRESS);
            MemorySegment cFilter = arena.allocateFrom(filter);
            int rc = (int) OPEN_EX.invokeExact(cFilter, layer, priority, flags, opts, out);
            if (rc < 0) {
                if (-rc == EBPFDivertSupport.EINVAL) {
                    MemorySegment msg = arena.allocate(ADDRESS);
                    MemorySegment pos = arena.allocate(JAVA_INT);
                    int crc = (int) COMPILE.invokeExact(cFilter, layer, msg, pos);
                    String text = cString(msg.get(ADDRESS, 0));
                    if (crc < 0 && text != null) {
                        throw new WinDivertException(-rc, "Invalid filter at position " + pos.get(JAVA_INT, 0) + ": " + text);
                    }
                }
                throw error(rc, "ebpfdivert_open");
            }
            return new PanamaHandle(out.get(ADDRESS, 0));
        } catch (Throwable t) {
            throw wrap(t);
        }
    }

    @Override
    public int recv(Handle handle, Buffer buffer, WinDivertAddress address) throws WinDivertException {
        MemorySegment h = ((PanamaHandle) handle).segment();
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment data = MemorySegment.ofBuffer(buffer.getByteBuffer());
            MemorySegment len = arena.allocate(JAVA_INT);
            MemorySegment addr = arena.allocate(AddressCodec.SIZE, 8);
            int rc = (int) RECV.invokeExact(h, data, buffer.capacity(), len, addr, -1);
            if (rc < 0) {
                throw error(rc, "ebpfdivert_recv");
            }
            if (address != null) {
                AddressCodec.decode(addr.asByteBuffer(), address);
            }
            return len.get(JAVA_INT, 0);
        } catch (Throwable t) {
            throw wrap(t);
        }
    }

    @Override
    public <T> WinDivertAsyncResult<T> recvAsync(Handle handle, int bufsize, WinDivertAsyncResult.ResultConverter<T> converter) throws WinDivertException {
        Buffer buffer = allocateBuffer(bufsize);
        WinDivertAddress address = new WinDivertAddress();
        return new WinDivertAsyncResult<>(handle, buffer, address, converter,
                EBPFDivertSupport.async(() -> recv(handle, buffer, address)));
    }

    @Override
    public int send(Handle handle, ByteBuffer packet, WinDivertAddress address) throws WinDivertException {
        Objects.requireNonNull(packet, "packet cannot be null");
        Objects.requireNonNull(address, "address cannot be null");
        MemorySegment h = ((PanamaHandle) handle).segment();
        try (Arena arena = Arena.ofConfined()) {
            int length = packet.remaining();
            MemorySegment data = arena.allocate(Math.max(1, length));
            data.copyFrom(MemorySegment.ofBuffer(packet.duplicate()));
            MemorySegment addr = arena.allocate(AddressCodec.SIZE, 8);
            addr.copyFrom(MemorySegment.ofArray(AddressCodec.encode(address)));
            MemorySegment sent = arena.allocate(JAVA_INT);
            int rc = (int) SEND.invokeExact(h, data, length, sent, addr);
            if (rc < 0) {
                throw error(rc, "ebpfdivert_send");
            }
            return sent.get(JAVA_INT, 0);
        } catch (Throwable t) {
            throw wrap(t);
        }
    }

    @Override
    public <T> WinDivertAsyncResult<T> sendAsync(Handle handle, ByteBuffer packet, WinDivertAddress address, WinDivertAsyncResult.ResultConverter<T> converter) throws WinDivertException {
        Buffer buffer = allocateBuffer(packet.remaining());
        ByteBuffer copy = buffer.getByteBuffer();
        copy.put(packet.duplicate());
        copy.flip();
        return new WinDivertAsyncResult<>(handle, buffer, address, converter,
                EBPFDivertSupport.async(() -> send(handle, copy, address)));
    }

    @Override
    public void shutdown(Handle handle, int how) throws WinDivertException {
        try {
            int rc = (int) SHUTDOWN.invokeExact(((PanamaHandle) handle).segment(), how);
            if (rc < 0) {
                throw error(rc, "ebpfdivert_shutdown");
            }
        } catch (Throwable t) {
            throw wrap(t);
        }
    }

    @Override
    public void setParam(Handle handle, int param, long value) throws WinDivertException {
        try {
            int rc = (int) SET_PARAM.invokeExact(((PanamaHandle) handle).segment(), param, value);
            if (rc < 0) {
                throw error(rc, "ebpfdivert_set_param");
            }
        } catch (Throwable t) {
            throw wrap(t);
        }
    }

    @Override
    public long getParam(Handle handle, int param) throws WinDivertException {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment value = arena.allocate(JAVA_LONG);
            int rc = (int) GET_PARAM.invokeExact(((PanamaHandle) handle).segment(), param, value);
            if (rc < 0) {
                throw error(rc, "ebpfdivert_get_param");
            }
            return value.get(JAVA_LONG, 0);
        } catch (Throwable t) {
            throw wrap(t);
        }
    }

    @Override
    public int calcChecksums(byte[] packet, WinDivertAddress address, long flags) {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment data = arena.allocate(Math.max(1, packet.length));
            MemorySegment.copy(packet, 0, data, JAVA_BYTE, 0, packet.length);
            int rc = (int) CALC_CHECKSUMS.invokeExact(data, packet.length, MemorySegment.NULL, flags);
            MemorySegment.copy(data, JAVA_BYTE, 0, packet, 0, packet.length);
            if (rc == 0 && address != null) {
                address.setIPChecksum(true);
                address.setTCPChecksum(true);
                address.setUDPChecksum(true);
            }
            return rc == 0 ? 1 : 0;
        } catch (Throwable t) {
            return 0;
        }
    }

    @Override
    public long hashPacket(byte[] packet, long seed) {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment data = arena.allocate(Math.max(1, packet.length));
            MemorySegment.copy(packet, 0, data, JAVA_BYTE, 0, packet.length);
            return (long) HASH.invokeExact(data, packet.length, seed);
        } catch (Throwable t) {
            return 0;
        }
    }

    @Override
    public Buffer allocateBuffer(int size) {
        final ByteBuffer bb = ByteBuffer.allocateDirect(Math.max(1, size));
        return new Buffer() {
            @Override
            public ByteBuffer getByteBuffer() {
                return bb;
            }

            @Override
            public int capacity() {
                return bb.capacity();
            }

            @Override
            public void close() {
            }
        };
    }

    @Override
    public String formatMessage(int errorCode) {
        try {
            return cString((MemorySegment) STRERROR.invokeExact(errorCode));
        } catch (Throwable t) {
            return "Error " + errorCode;
        }
    }

    @Override
    public int getLastError() {
        return 0;
    }

    /**
     * Detaches programs left behind by processes that died without closing their handles.
     */
    public void unregister() {
        try {
            int ignored = (int) UNREGISTER.invokeExact();
        } catch (Throwable ignored) {
        }
    }

    private static final class PanamaHandle implements Handle {
        private volatile MemorySegment segment;

        PanamaHandle(MemorySegment segment) {
            this.segment = segment;
        }

        MemorySegment segment() throws WinDivertException {
            MemorySegment s = segment;
            if (s == null) {
                throw new WinDivertException(9, "Handle is closed");
            }
            return s;
        }

        @Override
        public synchronized void close() throws WinDivertException {
            MemorySegment s = segment;
            if (s != null) {
                segment = null;
                try {
                    int ignored = (int) CLOSE.invokeExact(s);
                } catch (Throwable t) {
                    throw wrap(t);
                }
            }
        }

        @Override
        public boolean isValid() {
            return segment != null;
        }
    }
}
