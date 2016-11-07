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

import com.github.ffalcinelli.jdivert.Consts;
import org.junit.Before;
import org.junit.Test;

import java.nio.ByteBuffer;

import static com.github.ffalcinelli.jdivert.Consts.Protocol.ROUTING;
import static org.junit.Assert.*;

/**
 * Created by fabio on 26/10/2016.
 */
public abstract class IPv4IPHeaderTestCase extends IPHeaderTestCase {

    protected IPv4Header ipv4Hdr;
    protected int ipCksum;
    protected int ident;
    protected byte[] options;

    @Before
    public void setUp(){
        super.setUp();
        ipv4Hdr = new IPv4Header(ByteBuffer.wrap(rawData));
        ipHdr = ipv4Hdr;
        localhost = "127.0.0.1";
        ipVersion = 4;
    }

    @Test
    public void nextHeaderProtocolBis() {
        assertEquals(ipHdr.getNextHeaderProtocol(), ipv4Hdr.getProtocol());
    }

    @Test
    public void protocol() {
        assertEquals(protocol, ipv4Hdr.getProtocol());
        ipv4Hdr.setProtocol(ROUTING);
        assertEquals(ROUTING, ipv4Hdr.getProtocol());
    }

    @Test
    public void headerLengthBis() {
        assertNull(ipv4Hdr.getOptions());
        assertEquals(ipHeaderLength, ipHdr.getRawHeaderBytes().length);
    }

    @Test
    public void internetHeaderLength() {
        assertEquals(ipHdr.getHeaderLength(), ipv4Hdr.getInternetHeaderLength());
        ipv4Hdr.setInternetHeaderLength(24);
        assertEquals(24, ipv4Hdr.getInternetHeaderLength());
        assertEquals(24, ipv4Hdr.getHeaderLength());
        assertEquals(4, ipv4Hdr.getVersion());
        assertEquals(4, ipv4Hdr.getOptions().length);
    }

    @Test(expected = IllegalArgumentException.class)
    public void internetHeaderIllegalLength() {
        ipv4Hdr.setInternetHeaderLength(21);
    }

    @Test
    public void totalLength() {
        assertEquals(rawData.length, ipv4Hdr.getTotalLength());
        ipv4Hdr.setTotalLength((short) 21);
        assertEquals(21, ipv4Hdr.getTotalLength());
    }

    @Test
    public void flags() {
        for (IPv4Header.Flag flag : IPv4Header.Flag.values()) {
            assertFalse(ipv4Hdr.isFlag(flag));
            ipv4Hdr.setFlag(flag, true);
            assertTrue(ipv4Hdr.isFlag(flag));
        }
    }

    @Test
    public void checksum() {
        assertEquals(ipCksum, ipv4Hdr.getChecksum());
        ipv4Hdr.setChecksum((short) 21);
        assertEquals(21, ipv4Hdr.getChecksum());
    }

    @Test
    public void options() {
        assertEquals(null, ipv4Hdr.getOptions());
    }

    @Test(expected = IllegalStateException.class)
    public void optionsIllegalSize() {
        ipv4Hdr.setOptions(new byte[]{0x1, 0x2, 0x3, 0x4});
    }

    @Test
    public void adjustLengthAndAddOptions() {
        byte[] options = new byte[]{0x1, 0x2, 0x3, 0x4};
        ipv4Hdr.setInternetHeaderLength(24);
        ipv4Hdr.setOptions(options);
        assertArrayEquals(options, ipv4Hdr.getOptions());
    }

    @Test
    public void identification() {
        assertEquals(ident, ipv4Hdr.getIdentification());
        ipv4Hdr.setIdentification((short) 2);
        assertEquals(2, ipv4Hdr.getIdentification());
    }
}
