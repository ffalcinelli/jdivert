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

package com.github.ffalcinelli.jdivert;

import com.github.ffalcinelli.jdivert.exceptions.WinDivertException;
import com.github.ffalcinelli.jdivert.network.*;
import com.github.ffalcinelli.jdivert.windivert.WinDivertAddress;
import com.github.ffalcinelli.jdivert.windivert.WinDivertDLL;
import com.sun.jna.Memory;

import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;

import static com.github.ffalcinelli.jdivert.Consts.Direction;
import static com.github.ffalcinelli.jdivert.Util.printHexBinary;
import static com.github.ffalcinelli.jdivert.exceptions.WinDivertException.throwExceptionOnGetLastError;
import static com.sun.jna.platform.win32.WinDef.UINT;
import static com.sun.jna.platform.win32.WinDef.USHORT;

/**
 * Created by fabio on 21/10/2016.
 */
public class Packet {

    private ByteBuffer raw;
    private Direction direction;
    private int[] iface;
    private TransportHeader transHdr;
    private IPHeader ipHdr;
    private ICMPHeader icmpHdr;

    public Packet(byte[] raw, WinDivertAddress addr) {
        this(raw, new int[]{addr.IfIdx.intValue(), addr.SubIfIdx.intValue()},
                Direction.fromValue(addr.Direction.intValue()));
    }

    public Packet(byte[] raw, int[] iface, Direction direction) {
        if (iface.length != 2) {
            throw new IllegalArgumentException("Iface parameter must be a IfIdx, IfSubIdx pair");
        }
        this.raw = ByteBuffer.wrap(raw);
        this.raw.order(ByteOrder.BIG_ENDIAN);
        this.direction = direction;
        this.iface = iface;
        for (Header header : Header.buildHeaders(raw)) {
            if (header instanceof IPHeader) {
                ipHdr = (IPHeader) header;
            } else if (header instanceof ICMPHeader) {
                icmpHdr = (ICMPHeader) header;
            } else {
                transHdr = (TransportHeader) header;
            }
        }
    }

    public boolean isLoopback() {
        return iface[0] == 1;
    }

    public boolean isOutbound() {
        return direction == Direction.OUTBOUND;
    }

    public boolean isInbound() {
        return direction == Direction.INBOUND;
    }

    public boolean isIpv4() {
        return ipHdr instanceof IPv4Header;
    }

    public boolean isIpv6() {
        return ipHdr instanceof IPv6Header;
    }

    public boolean isIcmp() {
        return icmpHdr instanceof ICMPv4Header;
    }

    public boolean isIcmpv6() {
        return icmpHdr instanceof ICMPv6Header;
    }

    public boolean isUdp() {
        return transHdr instanceof UDPHeader;
    }

    public boolean isTcp() {
        return transHdr instanceof TCPHeader;
    }

    public TCPHeader getTcp() {
        return isTcp() ? (TCPHeader) transHdr : null;
    }

    public UDPHeader getUdp() {
        return isUdp() ? (UDPHeader) transHdr : null;
    }

    public ICMPv4Header getICMPv4() {
        return isIcmp() ? (ICMPv4Header) icmpHdr : null;
    }

    public ICMPv6Header getICMPv6() {
        return isIcmpv6() ? (ICMPv6Header) icmpHdr : null;
    }

    public IPv4Header getIPv4() {
        return isIpv4() ? (IPv4Header) ipHdr : null;
    }

    public IPv6Header getIPv6() {
        return isIpv6() ? (IPv6Header) ipHdr : null;
    }

    public String getSourceHostAddress() {
        return ipHdr.getSourceHostAddress();
    }

    public String getDestinationHostAddress() {
        return ipHdr.getDestinationHostAddress();
    }

    public int getSourcePort() {
        return transHdr.getSrcPort();
    }

    public void setSourcePort(int port) {
        transHdr.setSrcPort(port);
    }

    public int getDestinationPort() {
        return transHdr.getDstPort();
    }

    public void setDestinationPort(int port) {
        transHdr.setDstPort(port);
    }

    public void setSourceAddress(String address) throws UnknownHostException {
        ipHdr.setSourceHostAddress(address);
    }

    public void setDestinationAddress(String address) throws UnknownHostException {
        ipHdr.setDestinationHostAddress(address);
    }

    public byte[] getPayload() {
        int headersLen = ipHdr.getHeaderLength() + (transHdr != null ? transHdr.getHeaderLength() : icmpHdr.getHeaderLength());
        return Util.getBytesAtOffset(raw, headersLen, raw.capacity() - headersLen);
    }

    public byte[] getRaw() {
        return Util.getBytesAtOffset(raw, 0, raw.capacity());
    }

    public void recalculateChecksum(Consts.CalcChecksumsOption... options) throws WinDivertException {
        int flags = 0;
        for (Consts.CalcChecksumsOption option : options) {
            flags |= option.getValue();
        }
        byte[] rawBytes = getRaw();
        Memory memory = new Memory(rawBytes.length);
        memory.write(0, rawBytes, 0, rawBytes.length);
        WinDivertDLL.INSTANCE.WinDivertHelperCalcChecksums(memory, rawBytes.length, flags);
        throwExceptionOnGetLastError();

        Util.setBytesAtOffset(raw, 0, rawBytes.length,
                memory.getByteArray(0, rawBytes.length));
    }

    public WinDivertAddress getWinDivertAddress() {
        WinDivertAddress addr = new WinDivertAddress();
        addr.IfIdx = new UINT(iface[0]);
        addr.SubIfIdx = new UINT(iface[1]);
        addr.Direction = new USHORT(direction.getValue());
        return addr;
    }

    @Override
    public String toString() {
        return "Packet{" +
                "raw=" + printHexBinary(getRaw()) +
                ", direction=" + direction +
                ", iface=" + Arrays.toString(iface) +
                ", transHdr=" + transHdr +
                ", ipHdr=" + ipHdr +
                ", icmpHdr=" + icmpHdr +
                '}';
    }
}
