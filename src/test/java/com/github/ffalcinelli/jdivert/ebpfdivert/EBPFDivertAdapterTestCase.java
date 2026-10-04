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

import com.github.ffalcinelli.jdivert.Packet;
import com.github.ffalcinelli.jdivert.exceptions.WinDivertException;
import com.github.ffalcinelli.jdivert.windivert.NativeAdapter;
import com.github.ffalcinelli.jdivert.windivert.NativeAdapterFactory;
import com.github.ffalcinelli.jdivert.windivert.WinDivertAddress;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The libebpfdivert adapter in use (Panama on Java 22+, JNA before) against the real library.
 */
@EnabledOnOs(OS.LINUX)
public class EBPFDivertAdapterTestCase {

    private static final int NETWORK = 0;
    private static final long SNIFF = 1;
    private static final int QUEUE_LEN = 0;

    private final NativeAdapter adapter = NativeAdapterFactory.getAdapter();

    @AfterEach
    public void tearDown() {
        System.clearProperty("jdivert.ebpf.interfaces");
    }

    @Test
    public void helpers() throws Exception {
        byte[] packet = Packet.builder().ipv4("10.0.0.1", "10.0.0.2").udp(1000, 53).build().getRaw();
        assertEquals(adapter.hashPacket(packet, 1), adapter.hashPacket(packet, 1));
        assertNotEquals(adapter.hashPacket(packet, 1), adapter.hashPacket(packet, 2));
        assertEquals(1, adapter.calcChecksums(packet, null, 0));
        WinDivertAddress addr = new WinDivertAddress();
        assertEquals(0, adapter.calcChecksums(new byte[]{1, 2, 3}, addr, 0)); // not an IP packet
        assertFalse(addr.hasIPChecksum());
        assertNotNull(adapter.formatMessage(22));
        assertEquals(0, adapter.getLastError());
        // Not part of the adapter contract: detaches what dead processes left behind.
        adapter.getClass().getMethod("unregister").invoke(adapter);
    }

    @Test
    @EnabledIf("com.github.ffalcinelli.jdivert.CaptureCondition#canCapture")
    public void handleErrors() throws WinDivertException {
        System.setProperty("jdivert.ebpf.interfaces", "jdivert-none0");
        assertThrows(WinDivertException.class, () -> adapter.open("false", NETWORK, (short) 0, SNIFF));

        System.setProperty("jdivert.ebpf.interfaces", "lo");
        NativeAdapter.Handle h = adapter.open("false", NETWORK, (short) 0, SNIFF);
        assertTrue(h.isValid());
        assertThrows(WinDivertException.class, () -> adapter.getParam(h, 99));
        assertThrows(WinDivertException.class, () -> adapter.setParam(h, QUEUE_LEN, 0));
        assertThrows(WinDivertException.class, () -> adapter.shutdown(h, 0));
        try (NativeAdapter.Buffer buf = adapter.allocateBuffer(1500)) {
            assertEquals(-1, adapter.recv(h, buf, null, 0)); // nothing queued
        }

        h.close();
        h.close();
        assertFalse(h.isValid());
        assertThrows(WinDivertException.class, () -> adapter.getParam(h, QUEUE_LEN));
        assertThrows(WinDivertException.class, () -> adapter.setParam(h, QUEUE_LEN, 64));
        assertThrows(WinDivertException.class, () -> adapter.shutdown(h, 3));
    }

    @Test
    @EnabledIf("com.github.ffalcinelli.jdivert.CaptureCondition#canCapture")
    public void recvWithoutAddress() throws Exception {
        try (DatagramSocket socket = new DatagramSocket(0, InetAddress.getLoopbackAddress())) {
            int port = socket.getLocalPort();
            NativeAdapter.Handle h = adapter.open("udp.DstPort == " + port, NETWORK, (short) 0, SNIFF);
            try (NativeAdapter.Buffer buf = adapter.allocateBuffer(1500)) {
                byte[] data = "jdivert".getBytes();
                socket.send(new DatagramPacket(data, data.length, InetAddress.getLoopbackAddress(), port));
                assertEquals(28 + data.length, adapter.recv(h, buf, null, 2000));
            } finally {
                h.close();
            }
        }
    }
}
