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

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;

import static com.github.ffalcinelli.jdivert.Enums.Protocol;

/**
 * Created by fabio on 24/10/2016.
 */
public abstract class Ip<T extends InetAddress> extends Header {

    protected int srcAddrOffset = 12;
    protected int dstAddrOffset = 16;
    protected int addrLen = 4;

    public Ip(ByteBuffer raw) {
        super(raw);
    }

    public static int getVersion(ByteBuffer raw) {
        return raw.get(0) >> 4;
    }

    public T getInetAddressAtOffset(int offset) throws UnknownHostException {
        return (T) InetAddress.getByAddress(getBytesAtOffset(offset, addrLen));
    }

    public void setInetAddressAtOffset(int offset, T address) {
        byte[] addressBytes = address.getAddress();
        setBytesAtOffset(offset, addressBytes.length, addressBytes);
    }

    public T getSrcAddr() {
        try {
            return getInetAddressAtOffset(srcAddrOffset);
        } catch (UnknownHostException e) {
            return null;
        }
    }

    public void setSrcAddr(T address) {
        setInetAddressAtOffset(srcAddrOffset, address);
    }

    public T getDstAddr() {
        try {
            return getInetAddressAtOffset(dstAddrOffset);
        } catch (UnknownHostException e) {
            return null;
        }
    }

    public void setDstAddr(T address) {
        setInetAddressAtOffset(dstAddrOffset, address);
    }

    public String getSrcAddrStr() {
        InetAddress address = getSrcAddr();
        return address != null ? address.getHostAddress() : null;
    }

    public void setSrcAddrStr(String srcAddr) throws UnknownHostException {
        setSrcAddr((T) InetAddress.getByName(srcAddr));
    }

    public String getDstAddrStr() {
        InetAddress address = getDstAddr();
        return address != null ? address.getHostAddress() : null;
    }

    public void setDstAddrStr(String dstAddr) throws UnknownHostException {
        setDstAddr((T) InetAddress.getByName(dstAddr));
    }

    public int getVersion() {
        return raw.get(0) >> 4;
    }

    public void setVersion(int version) {
        raw.put(0, (byte) (version << 4));
    }

    public abstract Protocol getNextHeaderProtocol();
}
