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
public class UDPIPv4HeaderTestCase extends IPv4IPHeaderTestCase {

    protected UDPHeader udpHeader;
    protected int srcPort;
    protected int dstPort;
    protected String payload = "528e01000001000000000000013801380138013807696e2d61646472046172706100000c0001";

    @Before
    public void setUp() {
        rawDataHexString = "4500004281bf000040112191c0a82b09c0a82b01c9dd0035002ef268528e01000001000000000000013801380138013807696e2d61646472046172706100000c0001";
        super.setUp();
        udpHeader = new UDPHeader(ipHdr.getByteBuffer(), ipHdr.getHeaderLength());
        srcAddr = "192.168.43.9";
        dstAddr = "192.168.43.1";
        srcPort = 51677;
        dstPort = 53;
        protocol = UDP;
        ipHeaderLength = 20;
        ident = 33215;
        ipCksum = 8593;
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
    public void checksum() {
        super.checksum();
        assertEquals(62056, udpHeader.getChecksum());
        udpHeader.setChecksum(51234);
        assertEquals(51234, udpHeader.getChecksum());
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
