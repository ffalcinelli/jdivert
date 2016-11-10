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

import java.nio.ByteBuffer;

import static com.github.ffalcinelli.jdivert.Util.printHexBinary;

/**
 * Created by fabio on 25/10/2016.
 */
public class Udp extends Transport {

    public Udp(ByteBuffer raw, int start) {
        super(raw, start);
    }

    public int getLength() {
        return unsigned(raw.getShort(start + 4));
    }

    public void setLength(int length) {
        raw.putShort(start + 4, (short) length);
    }

    public int getChecksum() {
        return unsigned(raw.getShort(start + 6));
    }

    public void setChecksum(int cksum) {
        raw.putShort(start + 6, (short) cksum);
    }

    @Override
    public int getHeaderLength() {
        return 8;
    }

    public byte[] getData() {
        return getBytesAtOffset(start + getHeaderLength(), getLength() - getHeaderLength());
    }

    public void setData(byte[] data) {
        setBytesAtOffset(start + getHeaderLength(), data.length, data);
    }

    @Override
    public String toString() {
        return String.format("UDP {srcPort=%d, dstPort=%d, len=%d, cksum=%s, data=%s}"
                , getSrcPort()
                , getDstPort()
                , getLength()
                , Integer.toHexString(getChecksum())
                , printHexBinary(getData())
        );
    }
}
