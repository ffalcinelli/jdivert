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

package com.github.ffalcinelli.jdivert.network;

import org.junit.Before;
import org.junit.Test;

import static com.github.ffalcinelli.jdivert.Util.parseHexBinary;
import static com.github.ffalcinelli.jdivert.Consts.Protocol.UDP;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

/**
 * Created by fabio on 02/11/2016.
 */
public class UDPIPv6HeaderTestCase extends IPv6IPHeaderTestCase {

    protected UDPHeader udpHeader;
    protected int srcPort;
    protected int dstPort;
    protected String payload = "00060100000100000000000003777777057961686f6f03636f6d00000f0001";

    @Before
    public void setUp() {
        rawDataHexString = "60000000002711403ffe050700000001020086fffe0580da3ffe0501481900000000000000000042095d0035002746b700060100000100000000000003777777057961686f6f03636f6d00000f0001";
        super.setUp();
        udpHeader = new UDPHeader(ipHdr.getRaw(), ipHdr.getHeaderLength());
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
        assertEquals(udpHeader, headers[1]);
    }

    @Test
    public void srcPort() {
        assertEquals(srcPort, udpHeader.getSrcPort());
        udpHeader.setSrcPort(50000);
        assertEquals(50000, udpHeader.getSrcPort());
    }

    @Test
    public void dstPort() {
        assertEquals(53, udpHeader.getDstPort());
        udpHeader.setDstPort(50000);
        assertEquals(50000, udpHeader.getDstPort());
    }

    @Test
    public void length() {
        assertEquals(rawData.length - ipHdr.getHeaderLength(), udpHeader.getLength());
        udpHeader.setLength(51234);
        assertEquals(51234, udpHeader.getLength());
    }

    @Test
    public void data() {
        assertArrayEquals(parseHexBinary(payload), udpHeader.getData());
        byte[] data = new byte[]{0x0, 0x1, 0x2, 0x3};
        udpHeader.setLength(udpHeader.getHeaderLength() + data.length);
        udpHeader.setData(data);
        assertArrayEquals(data, udpHeader.getData());
    }

}
