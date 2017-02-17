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

import static com.github.ffalcinelli.jdivert.Util.unsigned;

/**
 * Created by fabio on 25/10/2016.
 */
public class Icmp extends Header {

    public Icmp(ByteBuffer raw, int start) {
        super(raw, start);
    }

    public byte getType() {
        return raw.get(start);
    }

    public void setType(byte type) {
        raw.put(start, type);
    }

    public byte getCode() {
        return raw.get(start + 1);
    }

    public void setCode(byte code) {
        raw.put(start + 1, code);
    }

    public int getChecksum() {
        return unsigned(raw.getShort(start + 2));
    }

    public void setChecksum(int cksum) {
        raw.putShort(start + 2, (short) cksum);
    }


    @Override
    public int getHeaderLength() {
        return 4;
    }
}
