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

import java.net.Inet4Address;
import java.nio.ByteBuffer;

import static com.github.ffalcinelli.jdivert.Consts.Protocol;
import static com.github.ffalcinelli.jdivert.Util.printHexBinary;
import static com.github.ffalcinelli.jdivert.Util.zeroPadArray;

/**
 * Created by fabio on 24/10/2016.
 */
public class IPv4Header extends IPHeader<Inet4Address> {

    public IPv4Header(ByteBuffer raw) {
        super(raw);
        srcAddrOffset = 12;
        dstAddrOffset = 16;
        addrLen = 4;
    }

    @Override
    public int getHeaderLength() {
        return getInternetHeaderLength();
    }

    public int getInternetHeaderLength() {
        return (raw.get(0) & 0x0F) * 4;
    }

    public void setInternetHeaderLength(int length) {
        if (length % 4 != 0) {
            throw new IllegalArgumentException("Length must be a multiple of 4");
        }
        byte first = (byte) (getVersion() << 4);
        byte second = (byte) ((length / 4) & 0x0F);
        raw.put(0, (byte) (first | second));
    }


    public int getTotalLength() {
        return unsigned(raw.getShort(2));
    }

    public void setTotalLength(int length) {
        raw.putShort(2, (short) length);
    }

    @Override
    public Protocol getNextHeaderProtocol() {
        return getProtocol();
    }

    public Protocol getProtocol() {
        return Protocol.fromValue(raw.get(9));
    }

    public void setProtocol(Protocol protocol) {
        raw.put(9, (byte) protocol.getValue());
    }

    public int getChecksum() {
        return unsigned(raw.getShort(10));
    }

    public void setChecksum(int cksum) {
        raw.putShort(10, (short) cksum);
    }

    public boolean isFlag(Flag flag) {
        return getFlag(6, flag.ordinal());
    }

    public void setFlag(Flag flag, boolean value) {
        setFlag(6, flag.ordinal(), value);
    }

    public byte[] getOptions() {
        if (getHeaderLength() - 20 > 0)
            return getBytesAtOffset(20, getHeaderLength() - 20);
        return null;
    }

    public void setOptions(byte[] options) {
        int delta = getHeaderLength() - 20;
        if (delta <= 0) {
            throw new IllegalStateException("Packet is too short for options.");
        }
        setBytesAtOffset(20, delta, zeroPadArray(options, delta));
    }

    public int getIdentification() {
        return unsigned(raw.getShort(4));
    }

    public void setIdentification(int ident) {
        raw.putShort(4, (short) ident);
    }

//    public short getFragmentOffset() {
//        return (short) (raw.getShort(4) >> 3);
//    }


    @Override
    public String toString() {
        StringBuilder flags = new StringBuilder();
        for (Flag flag : Flag.values()) {
            flags.append(flag.name()).append(isFlag(flag)).append(", ");
        }
        return "IPv4Header{" +
                "raw=" + printHexBinary(raw) +
                "version=" + getVersion() +
                ", tos=" +  //TODO
                ", totLength=" + getTotalLength() +
                ", ident=" + getIdentification() +
                ", flags={" + flags +
                "}, fragOffset=" + //TODO
                ", TTL=" + //TODO
                ", proto=" + getProtocol() +
                ", cksum=" + getChecksum() +
                ", srcAddr=" + getSourceHostAddress() +
                ", dstAddr=" + getDestinationHostAddress() +
                "}";
    }

    enum Flag {
        RESERVED, DF, MF
    }
}
