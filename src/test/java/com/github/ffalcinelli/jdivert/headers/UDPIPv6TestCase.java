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

import static com.github.ffalcinelli.jdivert.Enums.Protocol.UDP;
import static com.github.ffalcinelli.jdivert.Util.parseHexBinary;
import static org.junit.Assert.*;

/**
 * Created by fabio on 02/11/2016.
 */
public class UDPIPv6TestCase extends IPv6TestCase {

    protected Udp udp;
    protected int srcPort;
    protected int dstPort;
    protected String payload = "00060100000100000000000003777777057961686f6f03636f6d00000f0001";

    @Before
    public void setUp() {
        rawDataHexString = "60000000002711403ffe050700000001020086fffe0580da3ffe0501481900000000000000000042095d0035002746b700060100000100000000000003777777057961686f6f03636f6d00000f0001";
        super.setUp();
        udp = new Udp(ipHdr.getByteBuffer(), ipHdr.getHeaderLength());
        srcAddr = "3ffe:507:0:1:200:86ff:fe05:80da";
        dstAddr = "3ffe:501:4819:0:0:0:0:42";
        srcPort = 2397;
        dstPort = 53;
        protocol = UDP;
        ipHeaderLength = 40;
    }

    @Test
    public void buildHeadersBis() {
        Header[] headers = Header.buildHeaders(rawData);
        assertEquals(udp, headers[1]);
    }

    @Test
    public void srcPort() {
        assertEquals(srcPort, udp.getSrcPort());
        udp.setSrcPort(50000);
        assertEquals(50000, udp.getSrcPort());
        assertTrue(udp.toString().contains("srcPort=" + 50000));
    }

    @Test
    public void dstPort() {
        assertEquals(53, udp.getDstPort());
        udp.setDstPort(50000);
        assertEquals(50000, udp.getDstPort());
        assertTrue(udp.toString().contains("dstPort=" + 50000));
    }

    @Test
    public void length() {
        assertEquals(rawData.length - ipHdr.getHeaderLength(), udp.getLength());
        udp.setLength(51234);
        assertEquals(51234, udp.getLength());
    }

    @Test
    public void data() {
        assertArrayEquals(parseHexBinary(payload), udp.getData());
        byte[] data = new byte[]{0x0, 0x1, 0x2, 0x3};
        udp.setLength(udp.getHeaderLength() + data.length);
        udp.setData(data);
        assertArrayEquals(data, udp.getData());
    }

    @Test
    public void equalsAndHashCodeBis(){
        Udp udpHdr2 = new Udp(ipHdr.getByteBuffer(), ipHeaderLength);
        assertTrue(udp.equals(udpHdr2));
        assertEquals(udp.hashCode(), udpHdr2.hashCode());
    }
}
