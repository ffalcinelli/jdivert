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

import java.nio.ByteBuffer;

import static com.github.ffalcinelli.jdivert.Util.zeroPadArray;
import static com.github.ffalcinelli.jdivert.network.TCPHeader.Flag.NS;

/**
 * Created by fabio on 21/10/2016.
 */
public class TCPHeader extends TransportHeader {

    public TCPHeader(ByteBuffer raw, int offset) {
        super(raw, offset);
    }

    @Override
    public int getHeaderLength() {
        return getDataOffset() * 4;
    }

    public int getSequenceNumber() {
        return raw.getInt(start + 4);
    }

    public void setSequenceNumber(int seqNum) {
        raw.putInt(start + 4, seqNum);
    }

    public int getAckNumber() {
        return raw.getInt(start + 8);
    }

    public void setAckNumber(int ackNum) {
        raw.putInt(start + 8, ackNum);
    }

    public int getDataOffset() {
        return (raw.get(start + 12) & 0xF0) >> 4;
    }

    public void setDataOffset(int dataOffset) {
        raw.put(start + 12, (byte) (
                ((dataOffset << 4) & 0xF0) |
                        (raw.get(start + 12) & 0x0F)));
    }

    public boolean isFlag(Flag flag) {
        if (flag == NS) {
            return getFlag(start + 12, 7);
        } else {
            //Starts by 8 since NS belongs to the previous byte
            return getFlag(start + 13, flag.ordinal() - 1);
        }
    }

    public void setFlag(Flag flag, boolean value) {
        if (flag == NS) {
            setFlag(start + 12, 7, value);
        } else {
            //Starts by 8 since NS belongs to the previous byte
            setFlag(start + 13, flag.ordinal() - 1, value);
        }
    }

    public int getWindowSize() {
        return unsigned(raw.getShort(start + 14));
    }

    public void setWindowSize(int windowSize) {
        raw.putShort(start + 14, (short) windowSize);
    }

    public int getChecksum() {
        return unsigned(raw.getShort(start + 16));
    }

    public void setChecksum(int cksum) {
        raw.putShort(start + 16, (short) cksum);
    }

    public int getUrgentPointer() {
        return unsigned(raw.getShort(start + 18));
    }

    public void setUrgentPointer(int urgPtr) {
        raw.putShort(start + 18, (short) urgPtr);
    }

    public byte[] getOptions() {
        if (getHeaderLength() > 20)
            return getBytesAtOffset(start + 20, getHeaderLength() - 20);
        return null;
    }

    public void setOptions(byte[] options) {
        int delta = getHeaderLength() - 20;
        if (delta <= 0) {
            throw new IllegalStateException("Packet is too short for options.");
        }
        setBytesAtOffset(start + 20, delta, zeroPadArray(options, delta));
    }

    @Override
    public String toString() {
        StringBuilder flags = new StringBuilder();
        for (Flag flag : Flag.values()) {
            flags.append(", ").append(flag).append("=").append(isFlag(flag));
        }
        return "TCPHeader{" +
                "srcPort=" + getSrcPort() +
                ", dstPort=" + getDstPort() +
                ", flags={" + flags + "}" +
                ", window=" + getWindowSize() +
                ", cksum=" + getChecksum() +
                ", urgPtr=" + getUrgentPointer() +
//                ", opts=" + getOptions() +
                "}";
    }

    enum Flag {
        NS, CWR, ECE, URG, ACK, PSH, RST, SYN, FIN
    }
}
