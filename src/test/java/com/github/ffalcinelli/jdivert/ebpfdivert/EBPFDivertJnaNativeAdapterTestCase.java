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
import com.github.ffalcinelli.jdivert.windivert.NativeAdapter;
import com.github.ffalcinelli.jdivert.windivert.WinDivertAddress;
import com.sun.jna.Memory;
import com.sun.jna.Pointer;
import com.sun.jna.ptr.IntByReference;
import com.sun.jna.ptr.LongByReference;
import com.sun.jna.ptr.PointerByReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests the JNA adapter against an in-memory stand-in for libebpfdivert (no root needed).
 */
public class EBPFDivertJnaNativeAdapterTestCase {

    static final byte[] PACKET = {0x45, 0, 0, 20, 0, 0, 0x40, 0, 0x40, 0x11, 0, 0, 127, 0, 0, 1, 127, 0, 0, 1};

    static class FakeLib implements LibEbpfDivert {
        final Memory handle = new Memory(8);
        String openedFilter;
        String[] openedIfnames;
        int openRc;
        int closed;
        final List<byte[]> sent = new ArrayList<>();
        final List<Integer> sentIfIdx = new ArrayList<>();
        final List<Byte> sentOpaque = new ArrayList<>();
        long queueLen = 4096;
        int shutdownHow = -1;
        int recvRc, sendRc, shutdownRc, checksumRc, lastTimeout, unregistered;
        boolean compileWithoutMessage;

        public String ebpfdivert_version() { return "0.0.5"; }

        public int ebpfdivert_open_ex(String filter, int layer, short priority, long flags, OpenOpts opts, PointerByReference out) {
            openedFilter = filter;
            openedIfnames = opts.ifnames != null ? opts.ifnames.getStringArray(0) : null;
            if (openRc < 0) return openRc;
            out.setValue(handle);
            return 0;
        }

        public int ebpfdivert_recv(Pointer h, Pointer packet, int len, IntByReference recvLen, Pointer addr, int timeoutMs) {
            lastTimeout = timeoutMs;
            if (recvRc < 0) return recvRc;
            packet.write(0, PACKET, 0, PACKET.length);
            recvLen.setValue(PACKET.length);
            ByteBuffer a = addr.getByteBuffer(0, 80).order(ByteOrder.LITTLE_ENDIAN);
            a.putInt(8, 1 << 17);    // outbound
            a.putInt(16, 5);         // IfIdx
            a.put(40, (byte) 0x5A);  // backend-private context
            return 0;
        }

        public int ebpfdivert_send(Pointer h, Pointer packet, int len, IntByReference sendLen, Pointer addr) {
            if (sendRc < 0) return sendRc;
            sent.add(packet.getByteArray(0, len));
            ByteBuffer a = addr.getByteBuffer(0, 80).order(ByteOrder.LITTLE_ENDIAN);
            sentIfIdx.add(a.getInt(16));
            sentOpaque.add(a.get(40));
            sendLen.setValue(len);
            return 0;
        }

        public int ebpfdivert_shutdown(Pointer h, int how) { shutdownHow = how; return shutdownRc; }

        public int ebpfdivert_close(Pointer h) { closed++; return 0; }

        public int ebpfdivert_set_param(Pointer h, int param, long value) {
            if (param != 0 || value < 32) return -22;
            queueLen = value;
            return 0;
        }

        public int ebpfdivert_get_param(Pointer h, int param, LongByReference value) {
            if (param != 0) return -22;
            value.setValue(queueLen);
            return 0;
        }

        public int ebpfdivert_get_handle_stats(Pointer h, long[] stats, int n) { return 0; }

        public int ebpfdivert_unregister() { unregistered++; return 0; }

        public String ebpfdivert_strerror(int err) { return "errno " + Math.abs(err); }

        public int ebpfdivert_helper_compile_filter(String filter, int layer, PointerByReference errStr, IntByReference errPos) {
            if (!filter.contains("(")) return 0;
            if (compileWithoutMessage) return -22;
            Memory msg = new Memory(16);
            msg.setString(0, "Bad token");
            errStr.setValue(msg);
            errPos.setValue(9);
            return -22;
        }

        public int ebpfdivert_helper_eval_filter(String filter, Pointer packet, int len, Pointer addr) { return 1; }

        public int ebpfdivert_helper_calc_checksums(Pointer packet, int len, Pointer addr, long flags) {
            packet.setByte(10, (byte) 0xAB);
            return checksumRc;
        }

        public long ebpfdivert_helper_hash_packet(Pointer packet, int len, long seed) { return 99L + seed; }
    }

    private FakeLib lib;
    private EBPFDivertJnaNativeAdapter adapter;

    @BeforeEach
    public void setUp() {
        lib = new FakeLib();
        adapter = new EBPFDivertJnaNativeAdapter(lib);
    }

    @AfterEach
    public void tearDown() {
        System.clearProperty("jdivert.ebpf.interfaces");
    }

    @Test
    public void openPassesFilterAndInterfaces() throws WinDivertException {
        System.setProperty("jdivert.ebpf.interfaces", "eth0, lo");
        NativeAdapter.Handle h = adapter.open("udp", 0, (short) 10, 0);
        assertTrue(h.isValid());
        assertEquals("udp", lib.openedFilter);
        assertArrayEquals(new String[]{"eth0", "lo"}, lib.openedIfnames);
        h.close();
        h.close();
        assertEquals(1, lib.closed);
        assertFalse(h.isValid());
    }

    @Test
    public void openReportsFilterErrors() {
        lib.openRc = -22;
        WinDivertException e = assertThrows(WinDivertException.class, () -> adapter.open("tcp and (", 0, (short) 0, 0));
        assertTrue(e.getMessage().contains("position 9"), e.getMessage());
        lib.openRc = -1;
        e = assertThrows(WinDivertException.class, () -> adapter.open("tcp", 0, (short) 0, 0));
        assertEquals(1, e.getCode());
    }

    @Test
    public void recvThenSendRoundTripsTheAddress() throws WinDivertException {
        NativeAdapter.Handle h = adapter.open("udp", 0, (short) 0, 0);
        WinDivertAddress addr = new WinDivertAddress();
        try (NativeAdapter.Buffer buf = adapter.allocateBuffer(1500)) {
            int len = adapter.recv(h, buf, addr);
            assertEquals(PACKET.length, len);
            assertTrue(addr.isOutbound());
            assertEquals(5, addr.Union.Network.IfIdx);
            ByteBuffer bb = buf.getByteBuffer();
            bb.limit(len);
            assertEquals(PACKET.length, adapter.send(h, bb, addr));
        }
        assertArrayEquals(PACKET, lib.sent.get(0));
        assertEquals(5, lib.sentIfIdx.get(0));
        assertEquals((byte) 0x5A, lib.sentOpaque.get(0));
        // Heap buffers are copied.
        assertEquals(PACKET.length, adapter.send(h, ByteBuffer.wrap(PACKET), addr));
    }

    @Test
    public void asyncOperations() throws WinDivertException {
        NativeAdapter.Handle h = adapter.open("udp", 0, (short) 0, 0);
        WinDivertAsyncResult<Integer> r = adapter.recvAsync(h, 1500, (len, buf, addr) -> addr.isOutbound() ? len : -1);
        assertEquals(PACKET.length, r.get());
        WinDivertAsyncResult<Integer> s = adapter.sendAsync(h, ByteBuffer.wrap(PACKET), new WinDivertAddress(), (len, buf, addr) -> len);
        assertEquals(PACKET.length, s.get());
    }

    @Test
    public void paramsShutdownAndHelpers() throws WinDivertException {
        NativeAdapter.Handle h = adapter.open("udp", 0, (short) 0, 0);
        adapter.setParam(h, 0, 64);
        assertEquals(64, adapter.getParam(h, 0));
        assertThrows(WinDivertException.class, () -> adapter.setParam(h, 0, 1));
        assertThrows(WinDivertException.class, () -> adapter.getParam(h, 7));
        adapter.shutdown(h, 1);
        assertEquals(1, lib.shutdownHow);

        byte[] p = PACKET.clone();
        WinDivertAddress a = new WinDivertAddress();
        assertEquals(1, adapter.calcChecksums(p, a, 0));
        assertEquals((byte) 0xAB, p[10]);
        assertTrue(a.hasIPChecksum());
        assertEquals(100L, adapter.hashPacket(PACKET, 1));
        assertEquals("errno 22", adapter.formatMessage(22));

        h.close();
        assertThrows(WinDivertException.class, () -> adapter.getParam(h, 0));
    }

    @Test
    public void timedRecvAndNativeErrors() throws WinDivertException {
        NativeAdapter.Handle h = adapter.open("udp", 0, (short) 0, 0);
        try (NativeAdapter.Buffer buf = adapter.allocateBuffer(1500)) {
            assertEquals(PACKET.length, adapter.recv(h, buf, null, 250)); // the address is optional
            assertEquals(250, lib.lastTimeout);
            lib.recvRc = -11; // EAGAIN: nothing arrived in time
            assertEquals(-1, adapter.recv(h, buf, null, 0));
            assertEquals(0, lib.lastTimeout);
            lib.recvRc = -105;
            assertEquals(105, assertThrows(WinDivertException.class, () -> adapter.recv(h, buf, null, -7)).getCode());
            assertEquals(-1, lib.lastTimeout);
        }
        lib.sendRc = -90;
        assertEquals(90, assertThrows(WinDivertException.class,
                () -> adapter.send(h, ByteBuffer.wrap(PACKET), new WinDivertAddress())).getCode());
        lib.shutdownRc = -22;
        assertEquals(22, assertThrows(WinDivertException.class, () -> adapter.shutdown(h, 1)).getCode());
        h.close();
    }

    @Test
    public void openErrorWithoutCompilerMessage() {
        lib.openRc = -22;
        lib.compileWithoutMessage = true;
        WinDivertException e = assertThrows(WinDivertException.class, () -> adapter.open("tcp and (", 0, (short) 0, 0));
        assertEquals(22, e.getCode());
        assertFalse(e.getMessage().contains("position"), e.getMessage());
    }

    @Test
    public void checksumFailureLastErrorAndUnregister() {
        lib.checksumRc = -22;
        WinDivertAddress a = new WinDivertAddress();
        assertEquals(0, adapter.calcChecksums(PACKET.clone(), a, 0));
        assertFalse(a.hasIPChecksum());
        lib.checksumRc = 0;
        assertEquals(1, adapter.calcChecksums(PACKET.clone(), null, 0));
        assertEquals(0, adapter.getLastError());
        adapter.unregister();
        assertEquals(1, lib.unregistered);
    }

    @Test
    @EnabledOnOs(OS.LINUX)
    public void realLibraryIsLoadedOnce() throws WinDivertException {
        EBPFDivertJnaNativeAdapter first = new EBPFDivertJnaNativeAdapter();
        EBPFDivertJnaNativeAdapter second = new EBPFDivertJnaNativeAdapter();
        assertNotNull(first.formatMessage(22));
        assertEquals(first.formatMessage(22), second.formatMessage(22));
    }
}
