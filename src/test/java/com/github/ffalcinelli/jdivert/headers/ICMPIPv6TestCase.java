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

package com.github.ffalcinelli.jdivert.headers;

import org.junit.Before;
import org.junit.Test;

import static com.github.ffalcinelli.jdivert.Enums.Protocol.ICMPV6;
import static org.junit.Assert.*;

/**
 * Created by fabio on 02/11/2016.
 */
public class ICMPIPv6TestCase extends IPv6IPTestCase {

    protected Icmpv6 icmpHdr;
    protected byte[] messageBody = new byte[]{0x0, 0x0, 0x0, 0x0};

    @Before
    public void setUp() {
        rawDataHexString = "6000000000443a3d3ffe05010410000002c0dffffe47033e3ffe050700000001020086fffe0580da010413520000000060000000001411013ffe050700000001020086fffe0580da3ffe05010410000002c0dffffe47033ea07582a40014cf470a040000f9c8e7369d250b00";
        super.setUp();
        icmpHdr = new Icmpv6(ipHdr.getByteBuffer(), ipHdr.getHeaderLength());
        srcAddr = "3ffe:501:410:0:2c0:dfff:fe47:33e";
        dstAddr = "3ffe:507:0:1:200:86ff:fe05:80da";
        protocol = ICMPV6;
        ipHeaderLength = 40;
        hopLimit = 61;
    }

    @Test
    public void buildHeadersBis() {
        Header[] headers = Header.buildHeaders(rawData);
        assertEquals(icmpHdr, headers[1]);
    }

    @Test
    public void type() {
        assertEquals(1, icmpHdr.getType());
        icmpHdr.setType((byte) 0x2);
        assertEquals((byte) 0x2, icmpHdr.getType());
    }

    @Test
    public void code() {
        assertEquals(4, icmpHdr.getCode());
        icmpHdr.setCode((byte) 0xC);
        assertEquals((byte) 0xC, icmpHdr.getCode());
    }

    @Test
    public void messageBody() {
        assertArrayEquals(messageBody, icmpHdr.getMessageBody());
        byte[] data = new byte[]{0x0, 0x1, 0x2, 0x3};
        icmpHdr.setMessageBody(data);
        assertArrayEquals(data, icmpHdr.getMessageBody());
    }

    @Test
    public void equalsAndHashCodeBis() {
        Icmpv6 icmp2 = new Icmpv6(ipHdr.getByteBuffer(), ipHeaderLength);
        assertTrue(icmpHdr.equals(icmp2));
        assertEquals(icmpHdr.hashCode(), icmp2.hashCode());
    }
}
