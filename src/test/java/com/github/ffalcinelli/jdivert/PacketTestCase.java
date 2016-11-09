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
import com.sun.jna.platform.win32.WinDef;
import org.junit.Before;
import org.junit.Test;

import java.net.UnknownHostException;

import static com.github.ffalcinelli.jdivert.Enums.CalcChecksumsOption.NO_TCP_CHECKSUM;
import static com.github.ffalcinelli.jdivert.Enums.Direction.OUTBOUND;
import static com.github.ffalcinelli.jdivert.Util.parseHexBinary;
import static com.github.ffalcinelli.jdivert.Util.printHexBinary;
import static com.github.ffalcinelli.jdivert.headers.Tcp.Flag.FIN;
import static junit.framework.TestCase.assertFalse;
import static org.junit.Assert.*;

/**
 * Created by fabio on 03/11/2016.
 */
public class PacketTestCase {

    Packet packet;
    byte[] raw;
    byte[] payload;
    WinDivertAddress addr;
    String localhost = "127.0.0.1";

    @Before
    public void setUp() {
        addr = new WinDivertAddress();
        addr.IfIdx = new WinDef.UINT(0);
        addr.SubIfIdx = new WinDef.UINT(1);
        addr.Direction = new WinDef.USHORT(OUTBOUND.getValue());
        raw = parseHexBinary("45000051476040008006f005c0a856a936f274fdd84201bb0876cfd0c19f9320501800ff8dba0000170303" +
                "00240000000000000c2f53831a37ed3c3a632f47440594cab95283b558bf82cb7784344c3314");
        payload = parseHexBinary("17030300240000000000000c2f53831a37ed3c3a632f47440594cab95283b558bf82cb7784344c3314");

        packet = new Packet(raw, addr);
    }

    @Test(expected = IllegalArgumentException.class)
    public void constructWithIllegalIface() {
        packet = new Packet(raw, new int[]{0, 0, 0}, OUTBOUND);
    }

    @Test
    public void icmp() {
        packet = new Packet(parseHexBinary("4500005426ef0000400157f9c0a82b09080808080800bbb3d73b000051a7d67d000451e408090a0b0c0d0e0f101112131415161718191a1b1c1d1e1f202122232425262728292a2b2c2d2e2f3031323334353637"), addr);
        assertTrue(packet.isIcmpv4());
        assertNotNull(packet.getIcmpv4());
        assertFalse(packet.isTcp());
        assertNull(packet.getTcp());
        assertTrue(packet.isIpv4());
        assertNotNull(packet.getIpv4());
    }

    @Test
    public void tcp() {
        assertTrue(packet.isTcp());
        assertNotNull(packet.getTcp());
        assertTrue(packet.isOutbound());
        assertTrue(packet.isIpv4());
        assertNotNull(packet.getIpv4());
        assertFalse(packet.isLoopback());
        assertFalse(packet.isUdp());
        assertNull(packet.getUdp());
        assertFalse(packet.isIpv6());
        assertNull(packet.getIpv6());
        assertFalse(packet.isIcmpv4());
        assertNull(packet.getIcmpv4());
        assertFalse(packet.isIcmpv6());
        assertNull(packet.getIcmpv6());
        assertFalse(packet.isInbound());
        assertArrayEquals(payload, packet.getPayload());
        assertArrayEquals(raw, packet.getRaw());
        assertEquals(addr, packet.getWinDivertAddress());
        assertTrue(packet.toString().contains(printHexBinary(raw)));
    }

    @Test
    public void convenienceMethods() throws UnknownHostException {
        packet.setSrcAddr(localhost);
        assertEquals(localhost, packet.getSrcAddr());
        packet.setDstAddr(localhost);
        assertEquals(localhost, packet.getDstAddr());
    }


    @Test
    public void fin() {
        Packet p = new Packet(parseHexBinary("4500002841734000800600000A00020F0A00020FF4162B678A5FC6E30139B9515011080564650000"), addr);
        assertTrue(p.getTcp().is(FIN));
    }

    @Test
    public void equalsAndHashCode() {
        Packet p2 = new Packet(raw, addr);
        Packet p3 = new Packet(parseHexBinary("4500002841734000800600000A00020F0A00020FF4162B678A5FC6E30139B9515011080564650000"), addr);

        assertTrue(packet.equals(p2));
        assertEquals(packet.hashCode(), p2.hashCode());
        assertFalse(packet.equals(p3));
        assertNotEquals(packet.hashCode(), p3.hashCode());
    }

    @Test(expected = IllegalStateException.class)
    public void noDstPort() {
        Packet p = new Packet(parseHexBinary("4500003C5C8800007F011181C0A801010A00020F00005552000100096162636465666768696A6B6C6D6E6F7071727374757677616263646566676869"), addr);
        assertNull(p.getDstPort());
        p.setDstPort(8080);
    }

    @Test(expected = IllegalStateException.class)
    public void noSrcPort() {
        Packet p = new Packet(parseHexBinary("4500003C5C8800007F011181C0A801010A00020F00005552000100096162636465666768696A6B6C6D6E6F7071727374757677616263646566676869"), addr);
        assertNull(p.getSrcPort());
        p.setSrcPort(8080);
    }

    @Test
    public void excludeChecksums() throws WinDivertException {
        int cksum = packet.getTcp().getChecksum();
        packet.setSrcPort(8080);
        packet.recalculateChecksum(NO_TCP_CHECKSUM);
        assertEquals(cksum, packet.getTcp().getChecksum());
        packet.recalculateChecksum();
        assertNotEquals(cksum, packet.getTcp().getChecksum());
    }
}
