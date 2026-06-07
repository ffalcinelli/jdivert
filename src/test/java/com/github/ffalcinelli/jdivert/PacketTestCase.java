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

package com.github.ffalcinelli.jdivert;

import com.github.ffalcinelli.jdivert.exceptions.WinDivertException;
import com.github.ffalcinelli.jdivert.windivert.WinDivertAddress;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

import java.net.UnknownHostException;

import static com.github.ffalcinelli.jdivert.Enums.CalcChecksumsOption.NO_TCP_CHECKSUM;
import static com.github.ffalcinelli.jdivert.Enums.Direction.OUTBOUND;
import static com.github.ffalcinelli.jdivert.Util.parseHexBinary;
import static com.github.ffalcinelli.jdivert.Util.printHexBinary;
import static com.github.ffalcinelli.jdivert.headers.Tcp.Flag.FIN;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Created by fabio on 03/11/2016.
 */
public class PacketTestCase {

    Packet packet;
    byte[] raw;
    byte[] payload;
    WinDivertAddress addr;
    String localhost = "127.0.0.1";

    @BeforeEach
    public void setUp() {
        addr = new WinDivertAddress();
        addr.setLayer(0); // NETWORK
        addr.Union.Network.IfIdx = 0;
        addr.Union.Network.SubIfIdx = 1;
        addr.setOutbound(true);
        raw = parseHexBinary("45000051476040008006f005c0a856a936f274fdd84201bb0876cfd0c19f9320501800ff8dba0000170303" +
                "00240000000000000c2f53831a37ed3c3a632f47440594cab95283b558bf82cb7784344c3314");
        payload = parseHexBinary("17030300240000000000000c2f53831a37ed3c3a632f47440594cab95283b558bf82cb7784344c3314");

        packet = new Packet(raw, addr);
    }

    @Test
    public void constructWithIllegalIface() {
        assertThrows(IllegalArgumentException.class, () -> new Packet(raw, new int[]{0, 0, 0}, OUTBOUND));
    }

    @Test
    public void icmp() {
        packet = new Packet(parseHexBinary("4500005426ef0000400157f9c0a82b09080808080800bbb3d73b000051a7d67d000451e408090a0b0c0d0e0f101112131415161718191a1b1c1d1e1f202122232425262728292a2b2c2d2e2f3031323334353637"), addr);
        assertTrue(packet.isIcmpv4());
        assertTrue(packet.getIcmpv4().isPresent());
        assertFalse(packet.isTcp());
        assertFalse(packet.getTcp().isPresent());
        assertTrue(packet.isIpv4());
        assertTrue(packet.getIpv4().isPresent());
    }

    @Test
    public void tcp() {
        assertTrue(packet.isTcp());
        assertTrue(packet.getTcp().isPresent());
        assertTrue(packet.isOutbound());
        assertTrue(packet.isIpv4());
        assertTrue(packet.getIpv4().isPresent());
        assertFalse(packet.isLoopback());
        assertFalse(packet.isUdp());
        assertFalse(packet.getUdp().isPresent());
        assertFalse(packet.isIpv6());
        assertFalse(packet.getIpv6().isPresent());
        assertFalse(packet.isIcmpv4());
        assertFalse(packet.getIcmpv4().isPresent());
        assertFalse(packet.isIcmpv6());
        assertFalse(packet.getIcmpv6().isPresent());
        assertFalse(packet.isInbound());
        assertArrayEquals(payload, packet.getPayload());
        assertArrayEquals(raw, packet.getRaw());
        assertEquals(addr, packet.getWinDivertAddress());
        assertTrue(packet.toString().contains(printHexBinary(raw)));
    }

    @Test
    public void convenienceMethods() throws UnknownHostException {
        packet.setSrcAddr(localhost);
        assertEquals(localhost, packet.getSrcAddr().get());
        packet.setDstAddr(localhost);
        assertEquals(localhost, packet.getDstAddr().get());
    }


    @Test
    public void fin() {
        Packet p = new Packet(parseHexBinary("4500002841734000800600000A00020F0A00020FF4162B678A5FC6E30139B9515011080564650000"), addr);
        assertTrue(p.getTcp().get().is(FIN));
    }

    @Test
    public void equalsAndHashCode() {
        Packet p2 = new Packet(raw, addr);
        Packet p3 = new Packet(parseHexBinary("4500002841734000800600000A00020F0A00020FF4162B678A5FC6E30139B9515011080564650000"), addr);

        assertEquals(packet, p2);
        assertEquals(packet.hashCode(), p2.hashCode());
        assertNotEquals(packet, p3);
        assertNotEquals(packet.hashCode(), p3.hashCode());
    }

    @Test
    public void noDstPort() {
        Packet p = new Packet(parseHexBinary("4500003C5C8800007F011181C0A801010A00020F00005552000100096162636465666768696A6B6C6D6E6F7071727374757677616263646566676869"), addr);
        assertFalse(p.getDstPort().isPresent());
        assertThrows(IllegalStateException.class, () -> p.setDstPort(8080));
    }

    @Test
    public void noSrcPort() {
        Packet p = new Packet(parseHexBinary("4500003C5C8800007F011181C0A801010A00020F00005552000100096162636465666768696A6B6C6D6E6F7071727374757677616263646566676869"), addr);
        assertFalse(p.getSrcPort().isPresent());
        assertThrows(IllegalStateException.class, () -> p.setSrcPort(8080));
    }

    @Test
    public void isLoopback() {
        assertFalse(packet.isLoopback());
        addr.setLoopback(true);
        assertTrue(packet.isLoopback());
        
        addr.setLoopback(false);
        addr.setLayer(0); // NETWORK
        addr.Union.Network.IfIdx = 1;
        assertTrue(packet.isLoopback());
    }

    @Test
    public void emptyOptionals() {
        // TCP packet, so ICMP and UDP should be empty
        assertFalse(packet.getIcmpv4().isPresent());
        assertFalse(packet.getIcmpv6().isPresent());
        assertFalse(packet.getUdp().isPresent());
        assertFalse(packet.getIpv6().isPresent());
    }

    @Test
    public void setPortsNoTransport() {
        // ICMP packet has no transport header
        Packet icmp = new Packet(parseHexBinary("4500005426ef0000400157f9c0a82b09080808080800bbb3d73b000051a7d67d000451e408090a0b0c0d0e0f101112131415161718191a1b1c1d1e1f202122232425262728292a2b2c2d2e2f3031323334353637"), addr);
        assertThrows(IllegalStateException.class, () -> icmp.setSrcPort(80));
        assertThrows(IllegalStateException.class, () -> icmp.setDstPort(80));
    }

    @Test
    @EnabledOnOs(OS.WINDOWS)
    public void excludeChecksums() throws WinDivertException {
        int cksum = packet.getTcp().get().getChecksum();
        packet.setSrcPort(8080);
        packet.recalculateChecksum(NO_TCP_CHECKSUM);
        assertEquals(cksum, packet.getTcp().get().getChecksum());
        packet.recalculateChecksum();
        assertNotEquals(cksum, packet.getTcp().get().getChecksum());
    }

    @Test
    public void testSetPayloadReallocation() {
        byte[] newPayload = new byte[1000];
        for (int i = 0; i < 1000; i++) {
            newPayload[i] = (byte) (i % 256);
        }
        packet.setPayload(newPayload);
        assertArrayEquals(newPayload, packet.getPayload());
        assertEquals(raw.length - payload.length + 1000, packet.getRaw().length);
    }

    @Test
    public void testSetPayloadIPv6UDP() {
        String ipv6UdpHex = "60000000002711403ffe050700000001020086fffe0580da3ffe0501481900000000000000000042095d0035002746b700060100000100000000000003777777057961686f6f03636f6d00000f0001";
        Packet p = new Packet(parseHexBinary(ipv6UdpHex), addr);
        byte[] newPayload = new byte[]{0x1, 0x2, 0x3, 0x4};
        p.setPayload(newPayload);
        assertArrayEquals(newPayload, p.getPayload());
        assertTrue(p.isUdp());
        assertTrue(p.isIpv6());
        assertEquals(40 + 8 + 4, p.getRaw().length);
        assertEquals(40 + 8 + 4, p.getIpv6().get().getPayloadLength() + 40);
        assertEquals(8 + 4, p.getUdp().get().getLength());
    }
}
