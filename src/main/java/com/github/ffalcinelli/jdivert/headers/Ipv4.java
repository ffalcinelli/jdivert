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

import java.net.Inet4Address;
import java.nio.ByteBuffer;

import static com.github.ffalcinelli.jdivert.Enums.Protocol;
import static com.github.ffalcinelli.jdivert.Util.zeroPadArray;

/**
 * Created by fabio on 24/10/2016.
 */
public class Ipv4 extends Ip<Inet4Address> {

    public Ipv4(ByteBuffer raw) {
        super(raw);
        srcAddrOffset = 12;
        dstAddrOffset = 16;
        addrLen = 4;
    }

    @Override
    public int getHeaderLength() {
        return getIHL() * 4;
    }

    public int getIHL() {
        return (raw.get(0) & 0x0F);
    }

    public void setIHL(int length) {
        byte first = (byte) (getVersion() << 4);
        byte second = (byte) (length & 0x0F);
        raw.put(0, (byte) (first | second));
    }


    public int getTotalLength() {
        return unsigned(raw.getShort(2));
    }

    public void setTotalLength(int length) {
        raw.putShort(2, (short) length);
    }

    public int getTTL() {
        return unsigned(raw.get(8));
    }

    public void setTTL(int ttl) {
        raw.put(8, (byte) ttl);
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

    public boolean is(Flag flag) {
        return getFlag(6, flag.ordinal());
    }

    public void set(Flag flag, boolean value) {
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

    public int getID() {
        return unsigned(raw.getShort(4));
    }

    public void setID(int id) {
        raw.putShort(4, (short) id);
    }

    /**
     * Gets flags as a bitmask
     *
     * @return The bitmask representing flags
     */
    public int getFlags() {
        return raw.get(6) & 0x07;
    }

    /**
     * Sets flags with a bitmask
     *
     * @param flags The bitmask representing flags
     */
    public void setFlags(int flags) {
        byte first = (byte) (flags & 0x07);
        raw.put(6, (byte) (first | (getFragmentOffset() << 3) & 0x00FF));
    }

    public int getFragmentOffset() {
        return raw.getShort(6) >> 3;
    }

    public void setFragmentOffset(int fragOff) {
        raw.putShort(6, (short) (getFlags() | (fragOff << 3)));
    }


    @Override
    public String toString() {
        StringBuilder flags = new StringBuilder();
        for (Flag flag : Flag.values()) {
            flags.append(flag.name()).append("=").append(is(flag)).append(", ");
        }
        return String.format("IPv4 {version=%d, srcAddr=%s, dstAddr=%s, IHL=%d, TOS=%d, length=%d, ID=%s " +
                        "%s fragOff=%d TTL=%d " +
                        "proto=%s, cksum=%s}"
                , getVersion()
                , getSrcAddrStr()
                , getDstAddrStr()
                , getIHL()
                , 0 //TODO TOS
                , getTotalLength()
                , Integer.toHexString(getID())
                , flags
                , getFragmentOffset()
                , getTTL()
                , getProtocol()
                , Integer.toHexString(getChecksum())
        );
    }

    public enum Flag {
        RESERVED, DF, MF
    }
}
