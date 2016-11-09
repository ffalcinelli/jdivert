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

import static com.github.ffalcinelli.jdivert.Enums.Protocol.TCP;
import static com.github.ffalcinelli.jdivert.headers.Tcp.Flag.*;
import static org.junit.Assert.*;

/**
 * Created by fabio on 29/10/2016.
 */
public class TCPIPv6IpTestCase extends IPv6IPTestCase {

    Tcp tcpHdr;
    protected int tcpHdrLen;
    protected int seqNum;
    protected int ackNum;
    protected int windowSize;
    protected int urgPtr;
    protected int tcpCksum;
    protected byte[] options = new byte[]{1, 1, 8, 10, -128, 29, -91, 34, -128, 29, -91, 34};

    @Before
    public void setUp() {
        rawDataHexString = "600d684a007d0640fc000002000000020000000000000001fc000002000000010000000000000001a9a01f90021b638" +
                "dba311e8e801800cfc92e00000101080a801da522801da522474554202f68656c6c6f2e74787420485454502f312e31" +
                "0d0a557365722d4167656e743a206375726c2f372e33382e300d0a486f73743a205b666330303a323a303a313a3a315" +
                "d3a383038300d0a4163636570743a202a2f2a0d0a0d0a";
        super.setUp();
        tcpHdr = new Tcp(ipv6Hdr.getByteBuffer(), ipv6Hdr.getHeaderLength());
        srcAddr = "fc00:2:0:2:0:0:0:1";
        dstAddr = "fc00:2:0:1:0:0:0:1";
        protocol = TCP;
        ipHeaderLength = 40;
        tcpHdrLen = 32;
        seqNum = 35349389;
        ackNum = -1171186034;
        windowSize = 207;
        urgPtr = 0;
        tcpCksum = 51502;
    }

    @Test
    public void buildHeadersBis() {
        Header[] headers = Header.buildHeaders(rawData);
        assertEquals(tcpHdr, headers[1]);
    }

    @Test
    public void srcPort() {
        assertEquals(43424, tcpHdr.getSrcPort());
        tcpHdr.setSrcPort(51234);
        assertEquals(51234, tcpHdr.getSrcPort());
        assertTrue(tcpHdr.toString().contains("51234"));
    }

    @Test
    public void dstPort() {
        assertEquals(8080, tcpHdr.getDstPort());
        tcpHdr.setDstPort(51234);
        assertEquals(51234, tcpHdr.getDstPort());
        assertTrue(tcpHdr.toString().contains("51234"));
    }

    @Test
    public void headerLength() {
        super.headerLength();
        assertEquals(tcpHdrLen, tcpHdr.getHeaderLength());
        assertEquals(tcpHdr.getHeaderLength(), tcpHdr.getDataOffset() * 4);
    }

    @Test
    public void sequenceNumber() {
        assertEquals(seqNum, tcpHdr.getSeqNumber());
        tcpHdr.setSeqNumber(77);
        assertEquals(77, tcpHdr.getSeqNumber());
    }

    @Test
    public void ackNumber() {
        assertEquals(ackNum, tcpHdr.getAckNumber());
        tcpHdr.setAckNumber(11);
        assertEquals(11, tcpHdr.getAckNumber());
    }

    @Test
    public void dataOffset() {
        assertEquals(tcpHdr.getHeaderLength() / 4, tcpHdr.getDataOffset());
        tcpHdr.setDataOffset(6);
        assertEquals(6, tcpHdr.getDataOffset());
    }

    @Test
    public void flags() {
        for (Tcp.Flag flag : Tcp.Flag.values()) {
            if (flag == NS || flag == ACK || flag == PSH) {
                assertTrue(flag.name() + " is not false", tcpHdr.is(flag));
                tcpHdr.set(flag, false);
                assertFalse(flag.name() + " is not true", tcpHdr.is(flag));
            } else {
                assertFalse(flag.name() + " is not false", tcpHdr.is(flag));
                tcpHdr.set(flag, true);
                assertTrue(flag.name() + " is not true", tcpHdr.is(flag));
            }
        }
    }

    @Test
    public void windowSize() {
        assertEquals(windowSize, tcpHdr.getWindowSize());
        tcpHdr.setWindowSize((short) 12);
        assertEquals(12, tcpHdr.getWindowSize());
    }

    @Test
    public void checksum() {

        assertEquals(tcpCksum, tcpHdr.getChecksum());
        tcpHdr.setChecksum((short) 33);
        assertEquals(33, tcpHdr.getChecksum());
    }

    @Test
    public void urgentPointer() {
        assertEquals(urgPtr, tcpHdr.getUrgentPointer());
        tcpHdr.setUrgentPointer((short) 88);
        assertEquals(88, tcpHdr.getUrgentPointer());
    }

    @Test
    public void options() {
        assertArrayEquals(options, tcpHdr.getOptions());
        byte[] newOptions = new byte[]{0x1, 0x2, 0x3, 0x4};
        tcpHdr.setDataOffset(6);
        tcpHdr.setOptions(newOptions);
        assertArrayEquals(newOptions, tcpHdr.getOptions());
    }

    @Test(expected = IllegalStateException.class)
    public void optionsIllegalSizeTcp() {
        tcpHdr.setDataOffset(20);
        tcpHdr.setOptions(new byte[]{
                0x1, 0x2, 0x3, 0x4
        });
    }

    @Test
    public void equalsAndHashCodeBis(){
        Tcp tcpHdr2 = new Tcp(ipHdr.getByteBuffer(), ipHeaderLength);
        assertTrue(tcpHdr.equals(tcpHdr2));
        assertEquals(tcpHdr.hashCode(), tcpHdr2.hashCode());
    }
}
