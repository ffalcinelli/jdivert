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

import com.github.ffalcinelli.jdivert.Enums;
import org.junit.Before;
import org.junit.Test;

import java.nio.ByteBuffer;

import static com.github.ffalcinelli.jdivert.Enums.Protocol.ROUTING;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Created by fabio on 26/10/2016.
 */
public abstract class IPv6IPTestCase extends IPTestCase {

    protected Ipv6 ipv6Hdr;
    protected int hopLimit;

    @Before
    public void setUp() {
        super.setUp();
        ipv6Hdr = new Ipv6(ByteBuffer.wrap(rawData));
        ipHdr = ipv6Hdr;
        localhost = "0:0:0:0:0:0:0:1";
        ipVersion = 6;
        hopLimit = 64;
    }

    @Test
    public void versionBis() {
//        int ihl = ipv6Hdr.getIHL();
        ipv6Hdr.setVersion(5);
        assertEquals(5, ipv6Hdr.getVersion());
//        assertEquals(ihl, ipv4Hdr.getIHL());
    }

    @Test
    public void nextHeaderProtocolBis() {
        assertEquals(ipHdr.getNextHeaderProtocol(), ipv6Hdr.getNextHeader());
    }

    @Test
    public void nextHeader() {
        assertEquals(protocol, ipv6Hdr.getNextHeader());
        ipv6Hdr.setNextHeader(ROUTING);
        assertEquals(ROUTING, ipv6Hdr.getNextHeader());
    }

    @Test(expected = IllegalArgumentException.class)
    public void illegalNextHeader() {
        ipv6Hdr.setNextHeader(Enums.Protocol.fromValue(11));
    }

    @Test
    public void payloadLength() {
        assertEquals(rawData.length - ipHdr.getHeaderLength(), ipv6Hdr.getPayloadLength());
        ipv6Hdr.setPayloadLength((short) (rawData.length - 1));
        assertEquals(rawData.length - 1, ipv6Hdr.getPayloadLength());
    }

    @Test
    public void hopLimit() {
        assertEquals(hopLimit, ipv6Hdr.getHopLimit());
        ipv6Hdr.setHopLimit(hopLimit / 2);
        assertEquals(hopLimit / 2, ipv6Hdr.getHopLimit());
    }

    @Test
    public void equalsAndHashCode() {
        Ipv6 ipHdr2 = new Ipv6(ByteBuffer.wrap(rawData));
        assertTrue(ipHdr.equals(ipHdr2));
        assertEquals(ipHdr.hashCode(), ipHdr2.hashCode());
    }
}
