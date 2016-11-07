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

import com.github.ffalcinelli.jdivert.Util;

import java.nio.ByteBuffer;
import java.util.Arrays;

import static java.nio.ByteOrder.BIG_ENDIAN;

/**
 * Created by fabio on 24/10/2016.
 */
public abstract class Header {


    protected ByteBuffer raw;
    protected int start;

    public Header(ByteBuffer raw) {
        this(raw, 0);
    }

    public Header(ByteBuffer raw, int start) {
        //We duplicate the byte buffer so that an header can move indexes without affecting other code using the buffer
        this.raw = raw.duplicate();
        this.start = start;
    }

    public static Header[] buildHeaders(byte[] data) {
        ByteBuffer raw = ByteBuffer.wrap(data);
        raw.order(BIG_ENDIAN);
        IPHeader ipHdr;
        Header[] headers = new Header[2];
        if (IPHeader.getVersion(raw) == 4) {
            ipHdr = new IPv4Header(raw);
        } else {
            ipHdr = new IPv6Header(raw);
        }
        headers[0] = ipHdr;
        switch (ipHdr.getNextHeaderProtocol()) {
            case TCP:
                headers[1] = new TCPHeader(raw, ipHdr.getHeaderLength());
                break;
            case UDP:
                headers[1] = new UDPHeader(raw, ipHdr.getHeaderLength());
                break;
            case ICMP:
                headers[1] = new ICMPv4Header(raw, ipHdr.getHeaderLength());
                break;
            case ICMPV6:
                headers[1] = new ICMPv6Header(raw, ipHdr.getHeaderLength());
        }
        return headers;
    }

    public byte[] getBytesAtOffset(int offset, int length) {
        return Util.getBytesAtOffset(raw, offset, length);
    }

    public void setBytesAtOffset(int offset, int length, byte[] data) {
        Util.setBytesAtOffset(raw, offset, length, data);
    }

    public boolean getFlag(int index, int pos) {
        return (raw.get(index) & (1 << pos)) != 0;
    }

    public void setFlag(int index, int pos, boolean flag) {
        int value = raw.get(index);
        if (flag)
            value = value | (1 << pos);
        else
            value = value & ~(1 << pos);
        raw.put(index, (byte) value);
    }

    public byte[] getRawHeaderBytes() {
        return getBytesAtOffset(start, getHeaderLength());
    }

    /**
     * Return the Header length (in bytes)
     *
     * @return
     */
    public abstract int getHeaderLength();

    public int unsigned(short value) {
        return value & 0xffff;
    }

    public ByteBuffer getRaw() {
        return raw;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Header header = (Header) o;
        return Arrays.equals(getRawHeaderBytes(), header.getRawHeaderBytes());
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(getRawHeaderBytes());
    }
}
