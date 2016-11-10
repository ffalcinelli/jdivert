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
import static com.github.ffalcinelli.jdivert.headers.Tcp.Flag.ACK;
import static com.github.ffalcinelli.jdivert.headers.Tcp.Flag.PSH;
import static org.junit.Assert.*;

/**
 * Created by fabio on 29/10/2016.
 */
public class TCPIPv4IpTestCase extends IPv4IPTestCase {

    protected int tcpHdrLen;
    protected int seqNum;
    protected int ackNum;
    protected int windowSize;
    protected int urgPtr;
    protected int tcpCksum;
    Tcp tcpHdr;

    @Before
    public void setUp() {
        rawDataHexString = "45000051476040008006f005c0a856a936f274fdd84201bb0876cfd0c19f9320501800ff8dba0000170303" +
                "00240000000000000c2f53831a37ed3c3a632f47440594cab95283b558bf82cb7784344c3314";
        super.setUp();
        tcpHdr = new Tcp(ipv4Hdr.getByteBuffer(), ipv4Hdr.getHeaderLength());
        ipHdr = ipv4Hdr;
        srcAddr = "192.168.86.169";
        dstAddr = "54.242.116.253";
        protocol = TCP;
        ipHeaderLength = 20;
        ipCksum = 61445;
        ident = 18272;

        tcpHdrLen = 20;
        seqNum = 142004176;
        ackNum = -1046506720;
        windowSize = 255;
        urgPtr = 0;
        tcpCksum = 36282;
    }

    @Test
    public void buildHeadersBis() {
        Header[] headers = Header.buildHeaders(rawData);
        assertEquals(tcpHdr, headers[1]);
    }

    @Test
    public void srcPort() {
        assertEquals(55362, tcpHdr.getSrcPort());
        tcpHdr.setSrcPort(51234);
        assertEquals(51234, tcpHdr.getSrcPort());
        assertTrue(tcpHdr.toString().contains("srcPort=51234"));
    }

    @Test
    public void dstPort() {
        assertEquals(443, tcpHdr.getDstPort());
        tcpHdr.setDstPort(51234);
        assertEquals(51234, tcpHdr.getDstPort());
        assertTrue(tcpHdr.toString().contains("dstPort=51234"));
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
        super.flags();
        for (Tcp.Flag flag : Tcp.Flag.values()) {
            if (flag == ACK || flag == PSH) {
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
        super.checksum();
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
        super.options();
        assertNull(tcpHdr.getOptions());
        byte[] options = new byte[]{0x1, 0x2, 0x3, 0x4};
        tcpHdr.setDataOffset(6);
        tcpHdr.setOptions(options);
        assertArrayEquals(options, tcpHdr.getOptions());
    }

    @Test(expected = IllegalStateException.class)
    public void optionsIllegalSizeTcp() {
        tcpHdr.setOptions(new byte[]{0x1, 0x2, 0x3, 0x4});
    }

    @Test
    public void equalsAndHashCodeBis(){
        Tcp tcpHdr2 = new Tcp(ipHdr.getByteBuffer(), ipHeaderLength);
        assertTrue(tcpHdr.equals(tcpHdr2));
        assertEquals(tcpHdr.hashCode(), tcpHdr2.hashCode());
    }


}
