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
 * A single packet, possibly including an {@link com.github.ffalcinelli.jdivert.network.IPHeader IP header},
 * a {@link com.github.ffalcinelli.jdivert.network.TCPHeader TCP}/{@link com.github.ffalcinelli.jdivert.network.UDPHeader UDP} header and a payload.
 * <p>
 * Creation of packets is cheap, attributes are parsed when accessing them.
 * </p>
 * Created by fabio on 21/10/2016.
 */
public class Packet {

    private ByteBuffer raw;
    private Direction direction;
    private int[] iface;
    private TransportHeader transHdr;
    private IPHeader ipHdr;
    private ICMPHeader icmpHdr;

    /**
     * Construct a {@link Packet} from the given byte array and for the given {@link com.github.ffalcinelli.jdivert.windivert.WinDivertAddress} metadata.
     *
     * @param raw  The packet's array of bytes
     * @param addr The metadata (interface and direction)
     */
    public Packet(byte[] raw, WinDivertAddress addr) {
        this(raw, new int[]{addr.IfIdx.intValue(), addr.SubIfIdx.intValue()},
                Direction.fromValue(addr.Direction.intValue()));
    }

    /**
     * Construct a {@link Packet} from the given byte array and for the given metadata.
     *
     * @param raw       The packet's array of bytes
     * @param iface     The interface in form of {InterfaceIndex, InterfaceSubIndex} integer pair
     * @param direction The {@link com.github.ffalcinelli.jdivert.Consts.Direction Direction}
     */
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

    /**
     * Indicates if the packet is on the loopback interface
     *
     * @return True, if the packet is on the loopback interface, false otherwise.
     */
    public boolean isLoopback() {
        return iface[0] == 1;
    }

    /**
     * Convenience method to check if the packet is {@link com.github.ffalcinelli.jdivert.Consts.Direction#OUTBOUND OUTBOUND}
     *
     * @return True if packet is {@link com.github.ffalcinelli.jdivert.Consts.Direction#OUTBOUND OUTBOUND}, false otherwise
     */
    public boolean isOutbound() {
        return direction == Direction.OUTBOUND;
    }

    /**
     * Convenience method to check if the packet is {@link com.github.ffalcinelli.jdivert.Consts.Direction#INBOUND INBOUND}
     *
     * @return True if packet is {@link com.github.ffalcinelli.jdivert.Consts.Direction#INBOUND INBOUND}, false otherwise
     */

    public boolean isInbound() {
        return direction == Direction.INBOUND;
    }

    /**
     * Convenience method to check if the packet has a {@link com.github.ffalcinelli.jdivert.network.IPv4Header IP header version 4}
     *
     * @return True if packet is an IPv4 one
     */
    public boolean isIpv4() {
        return ipHdr instanceof IPv4Header;
    }

    /**
     * Convenience method to check if the packet has a {@link com.github.ffalcinelli.jdivert.network.IPv6Header IP header version 6}
     *
     * @return True if packet is an IPv6 one
     */
    public boolean isIpv6() {
        return ipHdr instanceof IPv6Header;
    }

    /**
     * Convenience method to check if the packet has a {@link com.github.ffalcinelli.jdivert.network.ICMPv4Header ICMP header version 4}
     *
     * @return True if packet is an ICMPv4 one
     */
    public boolean isIcmp() {
        return icmpHdr instanceof ICMPv4Header;
    }

    /**
     * Convenience method to check if the packet has a {@link com.github.ffalcinelli.jdivert.network.ICMPv6Header ICMP header version 6}
     *
     * @return True if packet is an ICMPv6 one
     */
    public boolean isIcmpv6() {
        return icmpHdr instanceof ICMPv6Header;
    }

    /**
     * Convenience method to check if the packet has a {@link com.github.ffalcinelli.jdivert.network.UDPHeader UDP header}
     *
     * @return True if packet is an UDP one
     */
    public boolean isUdp() {
        return transHdr instanceof UDPHeader;
    }

    /**
     * Convenience method to check if the packet has a {@link com.github.ffalcinelli.jdivert.network.TCPHeader TCP header}
     *
     * @return True if packet is an TCP one
     */
    public boolean isTcp() {
        return transHdr instanceof TCPHeader;
    }

    /**
     * Convenience method to get the {@link com.github.ffalcinelli.jdivert.network.TCPHeader} if present
     *
     * @return The {@link com.github.ffalcinelli.jdivert.network.TCPHeader} if present, {@code null} otherwise
     */
    public TCPHeader getTcp() {
        return isTcp() ? (TCPHeader) transHdr : null;
    }

    /**
     * Convenience method to get the {@link com.github.ffalcinelli.jdivert.network.UDPHeader} if present
     *
     * @return The {@link com.github.ffalcinelli.jdivert.network.UDPHeader} if present, {@code null} otherwise
     */
    public UDPHeader getUdp() {
        return isUdp() ? (UDPHeader) transHdr : null;
    }

    /**
     * Convenience method to get the {@link com.github.ffalcinelli.jdivert.network.ICMPv4Header} if present
     *
     * @return The {@link com.github.ffalcinelli.jdivert.network.ICMPv4Header} if present, {@code null} otherwise
     */
    public ICMPv4Header getICMPv4() {
        return isIcmp() ? (ICMPv4Header) icmpHdr : null;
    }

    /**
     * Convenience method to get the {@link com.github.ffalcinelli.jdivert.network.ICMPv6Header} if present
     *
     * @return The {@link com.github.ffalcinelli.jdivert.network.ICMPv6Header} if present, {@code null} otherwise
     */
    public ICMPv6Header getICMPv6() {
        return isIcmpv6() ? (ICMPv6Header) icmpHdr : null;
    }

    /**
     * Convenience method to get the {@link com.github.ffalcinelli.jdivert.network.IPv4Header} if present
     *
     * @return The {@link com.github.ffalcinelli.jdivert.network.IPv4Header} if present, {@code null} otherwise
     */
    public IPv4Header getIPv4() {
        return isIpv4() ? (IPv4Header) ipHdr : null;
    }

    /**
     * Convenience method to get the {@link com.github.ffalcinelli.jdivert.network.IPv6Header} if present
     *
     * @return The {@link com.github.ffalcinelli.jdivert.network.IPv6Header} if present, {@code null} otherwise
     */
    public IPv6Header getIPv6() {
        return isIpv6() ? (IPv6Header) ipHdr : null;
    }

    /**
     * Convenience method to get the String representing the source address
     *
     * @return The source address String
     */
    public String getSourceHostAddress() {
        return ipHdr.getSourceHostAddress();
    }

    /**
     * Convenience method to get the String representing the destination address
     *
     * @return The destination address String
     */
    public String getDestinationHostAddress() {
        return ipHdr.getDestinationHostAddress();
    }

    /**
     * Convenience method to get the source port number, if present.
     *
     * @return The source port number if present, {@code null} otherwise.
     */
    public Integer getSourcePort() {
        return transHdr != null ? transHdr.getSrcPort() : null;
    }

    /**
     * Convenience method to set the source port number
     *
     * @param port The port number to set for source service. If packet does not have such info an {@link java.lang.IllegalStateException} is thrown.
     */
    public void setSourcePort(int port) {
        if (transHdr != null)
            transHdr.setSrcPort(port);
        else
            throw new IllegalStateException("A port number cannot be set");
    }

    /**
     * Convenience method to get the destination port number, if present.
     *
     * @return The destination port number if present, {@code null} otherwise.
     */
    public int getDestinationPort() {
        return transHdr.getDstPort();
    }

    /**
     * Convenience method to set the destination port number
     *
     * @param port The port number to set for destination service. If packet does not have such info an {@link java.lang.IllegalStateException} is thrown.
     */
    public void setDestinationPort(int port) {
        if (transHdr != null)
            transHdr.setDstPort(port);
        else
            throw new IllegalStateException("A port number cannot be set");
    }

    /**
     * Convenience method to set the source address
     *
     * @param address The String representing the source address to set
     * @throws UnknownHostException Unlikely to be thrown...
     */
    public void setSourceAddress(String address) throws UnknownHostException {
        ipHdr.setSourceHostAddress(address);
    }

    /**
     * Convenience method to set the destination address
     *
     * @param address The String representing the destination address to set
     * @throws UnknownHostException Unlikely to be thrown...
     */
    public void setDestinationAddress(String address) throws UnknownHostException {
        ipHdr.setDestinationHostAddress(address);
    }

    /**
     * Get the {@link Packet} payload.
     *
     * @return The payload's array of bytes
     */
    public byte[] getPayload() {
        int headersLen = ipHdr.getHeaderLength() + (transHdr != null ? transHdr.getHeaderLength() : icmpHdr.getHeaderLength());
        return Util.getBytesAtOffset(raw, headersLen, raw.capacity() - headersLen);
    }

    /**
     * Get the {@link Packet} content (headers and payload) as an array of bytes.
     *
     * @return The packet's array of bytes
     */
    public byte[] getRaw() {
        return Util.getBytesAtOffset(raw, 0, raw.capacity());
    }

    /**
     * Recalculates the checksum fields matching the given {@link com.github.ffalcinelli.jdivert.Consts.CalcChecksumsOption options}
     *
     * @param options Drive the recalculateChecksum function
     * @throws WinDivertException Whenever the DLL call sets a LastError different by 0 (Success) or 997 (Overlapped I/O
     *                            is in progress)
     */
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

    /**
     * Put the {@link Packet} metadata into a {@link com.github.ffalcinelli.jdivert.windivert.WinDivertAddress} structure.
     * @return The {@link com.github.ffalcinelli.jdivert.windivert.WinDivertAddress} representing the packet metadata
     */
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
