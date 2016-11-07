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

import com.github.ffalcinelli.jdivert.windivert.WinDivertAddress;
import com.sun.jna.platform.win32.WinDef;
import org.junit.Before;
import org.junit.Test;

import static com.github.ffalcinelli.jdivert.Consts.Direction.OUTBOUND;
import static com.github.ffalcinelli.jdivert.Util.parseHexBinary;
import static com.github.ffalcinelli.jdivert.Util.printHexBinary;
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
        assertTrue(packet.isIcmp());
        assertNotNull(packet.getICMPv4());
        assertFalse(packet.isTcp());
        assertNull(packet.getTcp());
        assertTrue(packet.isIpv4());
        assertNotNull(packet.getIPv4());
    }

    @Test
    public void tcp() {
        assertTrue(packet.isTcp());
        assertNotNull(packet.getTcp());
        assertTrue(packet.isOutbound());
        assertTrue(packet.isIpv4());
        assertNotNull(packet.getIPv4());
        assertFalse(packet.isLoopback());
        assertFalse(packet.isUdp());
        assertNull(packet.getUdp());
        assertFalse(packet.isIpv6());
        assertNull(packet.getIPv6());
        assertFalse(packet.isIcmp());
        assertNull(packet.getICMPv4());
        assertFalse(packet.isIcmpv6());
        assertNull(packet.getICMPv6());
        assertFalse(packet.isInbound());
        assertArrayEquals(payload, packet.getPayload());
        assertArrayEquals(raw, packet.getRaw());
        assertEquals(addr, packet.getWinDivertAddress());
        assertTrue(packet.toString().contains(printHexBinary(raw)));
    }

}
