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
import com.github.ffalcinelli.jdivert.ebpfdivert.jna.LibEbpfDivert;
import com.github.ffalcinelli.jdivert.exceptions.WinDivertException;
import com.github.ffalcinelli.jdivert.windivert.AddressCodec;
import com.github.ffalcinelli.jdivert.windivert.DeployHandler;
import com.github.ffalcinelli.jdivert.windivert.NativeAdapter;
import com.github.ffalcinelli.jdivert.windivert.WinDivertAddress;
import com.sun.jna.Memory;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.StringArray;
import com.sun.jna.ptr.IntByReference;
import com.sun.jna.ptr.LongByReference;
import com.sun.jna.ptr.PointerByReference;

import java.nio.ByteBuffer;
import java.util.Objects;

/**
 * Linux {@link NativeAdapter} over libebpfdivert, through JNA (Java 8+).
 * <p>
 * libebpfdivert implements the WinDivert semantics with eBPF (filter language, re-injection,
 * priorities, parameters) and uses the {@code WINDIVERT_ADDRESS} layout, so this is a direct
 * mapping of the WinDivert calls.
 */
public class EBPFDivertJnaNativeAdapter implements NativeAdapter {

    private static volatile LibEbpfDivert shared;

    private final LibEbpfDivert lib;

    /**
     * Loads the bundled libebpfdivert.so.
     */
    public EBPFDivertJnaNativeAdapter() {
        this(load());
    }

    EBPFDivertJnaNativeAdapter(LibEbpfDivert lib) {
        this.lib = Objects.requireNonNull(lib, "lib");
    }

    private static LibEbpfDivert load() {
        LibEbpfDivert l = shared;
        if (l == null) {
            synchronized (EBPFDivertJnaNativeAdapter.class) {
                if (shared == null) {
                    shared = Native.load(DeployHandler.deployToPath().toString(), LibEbpfDivert.class);
                }
                l = shared;
            }
        }
        return l;
    }

    private WinDivertException error(int rc, String op) {
        return EBPFDivertSupport.error(rc, op, lib.ebpfdivert_strerror(rc));
    }

    private static Pointer pointerOf(ByteBuffer buffer) {
        return buffer.isDirect() ? Native.getDirectBufferPointer(buffer).share(buffer.position()) : null;
    }

    @Override
    public Handle open(String filter, int layer, short priority, long flags) throws WinDivertException {
        LibEbpfDivert.OpenOpts opts = new LibEbpfDivert.OpenOpts();
        String[] ifnames = EBPFDivertSupport.interfaces();
        StringArray names = ifnames != null ? new StringArray(ifnames) : null;
        opts.ifnames = names;
        opts.ring_bytes = EBPFDivertSupport.ringBytes();
        PointerByReference out = new PointerByReference();
        int rc = lib.ebpfdivert_open_ex(filter, layer, priority, flags, opts, out);
        if (rc < 0) {
            if (-rc == EBPFDivertSupport.EINVAL) {
                PointerByReference msg = new PointerByReference();
                IntByReference pos = new IntByReference();
                if (lib.ebpfdivert_helper_compile_filter(filter, layer, msg, pos) < 0 && msg.getValue() != null) {
                    throw new WinDivertException(-rc, "Invalid filter at position " + pos.getValue() + ": "
                            + msg.getValue().getString(0));
                }
            }
            throw error(rc, "ebpfdivert_open");
        }
        return new JnaHandle(lib, out.getValue());
    }

    @Override
    public int recv(Handle handle, Buffer buffer, WinDivertAddress address) throws WinDivertException {
        Pointer h = ((JnaHandle) handle).pointer();
        ByteBuffer bb = buffer.getByteBuffer();
        Memory addr = new Memory(AddressCodec.SIZE);
        IntByReference len = new IntByReference();
        int rc = lib.ebpfdivert_recv(h, Native.getDirectBufferPointer(bb), buffer.capacity(), len, addr, -1);
        if (rc < 0) {
            throw error(rc, "ebpfdivert_recv");
        }
        if (address != null) {
            AddressCodec.decode(addr.getByteBuffer(0, AddressCodec.SIZE), address);
        }
        return len.getValue();
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
        Pointer h = ((JnaHandle) handle).pointer();
        Pointer data = pointerOf(packet);
        if (data == null) {
            Memory copy = new Memory(Math.max(1, packet.remaining()));
            ByteBuffer src = packet.duplicate();
            byte[] bytes = new byte[src.remaining()];
            src.get(bytes);
            copy.write(0, bytes, 0, bytes.length);
            data = copy;
        }
        Memory addr = new Memory(AddressCodec.SIZE);
        addr.write(0, AddressCodec.encode(address), 0, AddressCodec.SIZE);
        IntByReference sent = new IntByReference();
        int rc = lib.ebpfdivert_send(h, data, packet.remaining(), sent, addr);
        if (rc < 0) {
            throw error(rc, "ebpfdivert_send");
        }
        return sent.getValue();
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
        int rc = lib.ebpfdivert_shutdown(((JnaHandle) handle).pointer(), how);
        if (rc < 0) {
            throw error(rc, "ebpfdivert_shutdown");
        }
    }

    @Override
    public void setParam(Handle handle, int param, long value) throws WinDivertException {
        int rc = lib.ebpfdivert_set_param(((JnaHandle) handle).pointer(), param, value);
        if (rc < 0) {
            throw error(rc, "ebpfdivert_set_param");
        }
    }

    @Override
    public long getParam(Handle handle, int param) throws WinDivertException {
        LongByReference value = new LongByReference();
        int rc = lib.ebpfdivert_get_param(((JnaHandle) handle).pointer(), param, value);
        if (rc < 0) {
            throw error(rc, "ebpfdivert_get_param");
        }
        return value.getValue();
    }

    @Override
    public int calcChecksums(byte[] packet, WinDivertAddress address, long flags) {
        Memory memory = new Memory(Math.max(1, packet.length));
        memory.write(0, packet, 0, packet.length);
        int rc = lib.ebpfdivert_helper_calc_checksums(memory, packet.length, null, flags);
        memory.read(0, packet, 0, packet.length);
        if (rc == 0 && address != null) {
            address.setIPChecksum(true);
            address.setTCPChecksum(true);
            address.setUDPChecksum(true);
        }
        // WinDivertHelperCalcChecksums returns TRUE on success.
        return rc == 0 ? 1 : 0;
    }

    @Override
    public long hashPacket(byte[] packet, long seed) {
        Memory memory = new Memory(Math.max(1, packet.length));
        memory.write(0, packet, 0, packet.length);
        return lib.ebpfdivert_helper_hash_packet(memory, packet.length, seed);
    }

    @Override
    public Buffer allocateBuffer(int size) {
        return new DirectBuffer(size);
    }

    @Override
    public String formatMessage(int errorCode) {
        return lib.ebpfdivert_strerror(errorCode);
    }

    @Override
    public int getLastError() {
        return 0;
    }

    /**
     * Detaches programs left behind by processes that died without closing their handles.
     */
    public void unregister() {
        lib.ebpfdivert_unregister();
    }

    private static final class JnaHandle implements Handle {
        private final LibEbpfDivert lib;
        private volatile Pointer pointer;

        JnaHandle(LibEbpfDivert lib, Pointer pointer) {
            this.lib = lib;
            this.pointer = pointer;
        }

        Pointer pointer() throws WinDivertException {
            Pointer p = pointer;
            if (p == null) {
                throw new WinDivertException(9, "Handle is closed");
            }
            return p;
        }

        @Override
        public synchronized void close() throws WinDivertException {
            Pointer p = pointer;
            if (p != null) {
                pointer = null;
                lib.ebpfdivert_close(p);
            }
        }

        @Override
        public boolean isValid() {
            return pointer != null;
        }
    }

    static final class DirectBuffer implements Buffer {
        private final ByteBuffer byteBuffer;

        DirectBuffer(int size) {
            this.byteBuffer = ByteBuffer.allocateDirect(Math.max(1, size));
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
}
