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

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;

import static com.github.ffalcinelli.jdivert.Consts.Protocol;

/**
 * Created by fabio on 24/10/2016.
 */
public abstract class IPHeader<T extends InetAddress> extends Header {

    protected int srcAddrOffset = 12;
    protected int dstAddrOffset = 16;
    protected int addrLen = 4;

    public IPHeader(ByteBuffer raw) {
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

    public T getSourceAddress() {
        try {
            return getInetAddressAtOffset(srcAddrOffset);
        } catch (UnknownHostException e) {
            return null;
        }
    }

    public void setSourceAddress(T address) {
        setInetAddressAtOffset(srcAddrOffset, address);
    }

    public T getDestinationAddress() {
        try {
            return getInetAddressAtOffset(dstAddrOffset);
        } catch (UnknownHostException e) {
            return null;
        }
    }

    public void setDestinationAddress(T address) {
        setInetAddressAtOffset(dstAddrOffset, address);
    }

    public String getSourceHostAddress() {
        InetAddress address = getSourceAddress();
        return address != null ? address.getHostAddress() : null;
    }

    public void setSourceHostAddress(String srcAddr) throws UnknownHostException {
        setSourceAddress((T) InetAddress.getByName(srcAddr));
    }

    public String getDestinationHostAddress() {
        InetAddress address = getDestinationAddress();
        return address != null ? address.getHostAddress() : null;
    }

    public void setDestinationHostAddress(String srcAddr) throws UnknownHostException {
        setDestinationAddress((T) InetAddress.getByName(srcAddr));
    }
//
//    public void setHeaderLength(int length) {
//        raw.put((byte) (getVersion() | (((byte) length / 4) & 0x0F)));
//    }

    public abstract int getHeaderLength();

    public int getVersion() {
        return raw.get(0) >> 4;
    }

    public void setVersion(int version) {
        raw.put(0, (byte) (version << 4));
    }

    public abstract Protocol getNextHeaderProtocol();
}
