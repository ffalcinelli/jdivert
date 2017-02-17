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

import java.net.Inet6Address;
import java.nio.ByteBuffer;

import static com.github.ffalcinelli.jdivert.Enums.Protocol;
import static com.github.ffalcinelli.jdivert.Util.unsigned;

/**
 * Created by fabio on 24/10/2016.
 */
public class Ipv6 extends Ip<Inet6Address> {


    public Ipv6(ByteBuffer raw) {
        super(raw);
        setSrcAddrOffset(8);
        setDstAddrOffset(24);
        setAddrLen(16);
    }

    @Override
    public int getHeaderLength() {
        return 40;
    }

    public void setVersion(int version) {
        raw.put(0, (byte) ((version << 4)));
    }

    @Override
    public Protocol getNextHeaderProtocol() {
        return getNextHeader();
    }

    public int getPayloadLength() {
        return unsigned(raw.getShort(4));
    }

    public void setPayloadLength(short length) {
        raw.putShort(4, length);
    }

    public Protocol getNextHeader() {
        return Protocol.fromValue(raw.get(6));
    }

    public void setNextHeader(Protocol protocol) {
        raw.put(6, (byte) protocol.getValue());
    }

    public int getHopLimit() {
        return raw.get(7);
    }

    public void setHopLimit(int hopLimit) {
        raw.put(7, (byte) hopLimit);
    }

    @Override
    public String toString() {
        return String.format("IPv6 {version=%d, srcAddr=%s, dstAddr=%s, payloadLength=%d, nextHeader=%s, hopLimit=%d}"
                , getVersion()
                , getSrcAddrStr()
                , getDstAddrStr()
                , getPayloadLength()
                , getNextHeader()
                , getHopLimit()
        );
    }
}
