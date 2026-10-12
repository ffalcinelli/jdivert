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

package com.github.ffalcinelli.jdivert;

import com.github.ffalcinelli.jdivert.headers.Ipv4;
import com.github.ffalcinelli.jdivert.windivert.WinDivertAddress;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Optional;

import static com.github.ffalcinelli.jdivert.Util.parseHexBinary;
import static com.github.ffalcinelli.jdivert.headers.Ipv4.Flag.DF;
import static com.github.ffalcinelli.jdivert.headers.Ipv4.Flag.MF;
import static com.github.ffalcinelli.jdivert.headers.Ipv4.Flag.RESERVED;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Header parsing of IP fragments and IPv6 extension headers, which must match WinDivert's packet parser.
 */
public class FragmentTestCase {

    private static final String TCP = "00501f90000000000000000050022000917c0000";
    private static final String PAYLOAD = "0102030405060708";
    private static final String LOOPBACK6 = "00000000000000000000000000000001";

    private static Packet packet(String hex) {
        return new Packet(parseHexBinary(hex), new WinDivertAddress());
    }

    /** IPv4 header + TCP header + 8 bytes of payload, with the given flags/fragment offset field. */
    private static Packet ipv4Tcp(String flagsFragOff) {
        return packet("45000030000a" + flagsFragOff + "40060000" + "7f000001" + "7f000001" + TCP + PAYLOAD);
    }

    /** IPv6 header with the given first next header, followed by {@code rest}. */
    private static Packet ipv6(int nextHeader, String rest) {
        return packet(String.format("60000000%04x%02x40", rest.length() / 2, nextHeader) + LOOPBACK6 + LOOPBACK6 + rest);
    }

    private static String fragmentHeader(int offsetUnits, boolean more) {
        return String.format("0600%04x0000002a", (offsetUnits << 3) | (more ? 1 : 0));
    }

    @Test
    public void ipv4FirstFragmentHasTransportHeader() {
        Packet p = ipv4Tcp("2000");
        assertTrue(p.getIpv4().get().is(MF));
        assertTrue(p.isTcp());
        assertEquals(Optional.of(80), p.getSrcPort());
        assertArrayEquals(parseHexBinary(PAYLOAD), p.getPayload());
    }

    @Test
    public void ipv4LaterFragmentHasNoTransportHeader() {
        Packet p = ipv4Tcp("0002");
        assertEquals(2, p.getIpv4().get().getFragmentOffset());
        assertFalse(p.isTcp());
        assertFalse(p.getTcp().isPresent());
        assertFalse(p.getSrcPort().isPresent());
        assertEquals(20, p.getHeadersLength());
        assertArrayEquals(parseHexBinary(TCP + PAYLOAD), p.getPayload());
    }

    @Test
    public void ipv4LaterFragmentChecksumKeepsPayload() {
        Packet p = ipv4Tcp("0002");
        byte[] before = p.getRaw();
        p.recalculateChecksum();
        byte[] after = p.getRaw();
        assertArrayEquals(Arrays.copyOfRange(before, 20, before.length), Arrays.copyOfRange(after, 20, after.length));
        assertTrue(p.getIpv4().get().getChecksum() != 0);
    }

    @Test
    public void ipv4UnknownProtocol() {
        // GRE (47), which Enums.Protocol does not list
        Packet p = packet("45000018000a0000402f0000" + "7f000001" + "7f000001" + "00000800");
        assertEquals(47, p.getIpv4().get().getTransportProtocol());
        assertFalse(p.isTcp());
        assertFalse(p.isUdp());
        assertFalse(p.getIcmp().isPresent());
    }

    @Test
    public void ipv4FlagBits() {
        Ipv4 ip = ipv4Tcp("0000").getIpv4().get();
        ip.set(MF, true);
        assertEquals(0x1, ip.getFlags());
        assertFalse(ip.is(RESERVED));
        ip.set(MF, false);
        ip.set(RESERVED, true);
        assertEquals(0x4, ip.getFlags());
        assertFalse(ip.is(MF));
        ip.set(RESERVED, false);
        ip.set(DF, true);
        assertEquals(0x2, ip.getFlags());
    }

    @Test
    public void ipv4SetFlagsKeepsLargeFragmentOffset() {
        Ipv4 ip = ipv4Tcp("0000").getIpv4().get();
        ip.setFragmentOffset(291);
        ip.setFlags(0x1);
        assertEquals(291, ip.getFragmentOffset());
        assertTrue(ip.is(MF));
    }

    @Test
    public void ipv6ExtensionHeadersAreSkipped() {
        // Hop-by-Hop Options (8 bytes, PadN) then TCP
        Packet p = ipv6(0, "0600010400000000" + TCP + PAYLOAD);
        assertTrue(p.isTcp());
        assertEquals(48, p.getTcp().get().getStart());
        assertEquals(Optional.of(8080), p.getDstPort());
        assertArrayEquals(parseHexBinary(PAYLOAD), p.getPayload());
    }

    @Test
    public void ipv6FirstFragmentHasTransportHeader() {
        Packet p = ipv6(44, fragmentHeader(0, true) + TCP + PAYLOAD);
        assertFalse(p.getIpv6().get().isLaterFragment());
        assertTrue(p.isTcp());
        assertEquals(Optional.of(80), p.getSrcPort());
    }

    @Test
    public void ipv6LaterFragmentHasNoTransportHeader() {
        Packet p = ipv6(44, fragmentHeader(2, false) + TCP + PAYLOAD);
        assertTrue(p.getIpv6().get().isLaterFragment());
        assertEquals(6, p.getIpv6().get().getTransportProtocol());
        assertFalse(p.isTcp());
        assertFalse(p.getDstPort().isPresent());
        assertEquals(48, p.getHeadersLength());
        assertArrayEquals(parseHexBinary(TCP + PAYLOAD), p.getPayload());
    }

    @Test
    public void ipv6TruncatedExtensionHeader() {
        Packet p = ipv6(44, "06000000");
        assertEquals(44, p.getIpv6().get().getTransportProtocol());
        assertEquals(40, p.getIpv6().get().getTransportOffset());
        assertFalse(p.isTcp());
    }

    @Test
    public void ipv6AuthenticationHeaderIsSkipped() {
        // AH with payload length 1: (1 + 2) * 4 = 12 bytes, then TCP
        Packet p = ipv6(51, "060100000000000100000001" + TCP + PAYLOAD);
        assertTrue(p.isTcp());
        assertEquals(52, p.getTcp().get().getStart());
        assertEquals(72, p.getHeadersLength());
        assertArrayEquals(parseHexBinary(PAYLOAD), p.getPayload());
    }

    @Test
    public void ipv6ExtensionHeaderAtEndOfPacket() {
        // Hop-by-Hop Options announcing Destination Options, but the packet ends there
        Packet p = ipv6(0, "3c00010400000000");
        assertEquals(60, p.getIpv6().get().getTransportProtocol());
        assertEquals(48, p.getIpv6().get().getTransportOffset());
        assertFalse(p.isTcp());
        assertEquals(48, p.getHeadersLength());
    }

    @Test
    public void icmpProtocolMustMatchIpVersion() {
        // ICMPv4 inside IPv6 and ICMPv6 inside IPv4 are not parsed as ICMP
        Packet v6 = ipv6(1, "0800f7ff00000000");
        assertFalse(v6.getIcmp().isPresent());
        assertEquals(40, v6.getHeadersLength());
        Packet v4 = packet("4500001c000a0000403a0000" + "7f000001" + "7f000001" + "8000000000000000");
        assertFalse(v4.getIcmp().isPresent());
        assertEquals(20, v4.getHeadersLength());
    }

    @Test
    public void icmpHeadersLength() {
        Packet p = packet("4500001c000a000040010000" + "7f000001" + "7f000001" + "0800f7ff00000000");
        assertTrue(p.isIcmpv4());
        assertEquals(24, p.getHeadersLength());
    }

    @Test
    public void noIpHeaderHasNoHeadersLength() {
        assertEquals(0, packet("0102030405060708").getHeadersLength());
    }

    @Test
    public void ipv6SecondFragmentHeaderStopsTheWalk() {
        Packet p = ipv6(44, fragmentHeader(0, true).replaceFirst("^06", "2c") + fragmentHeader(0, false) + TCP);
        assertEquals(44, p.getIpv6().get().getTransportProtocol());
        assertEquals(48, p.getIpv6().get().getTransportOffset());
        assertFalse(p.isTcp());
    }
}
