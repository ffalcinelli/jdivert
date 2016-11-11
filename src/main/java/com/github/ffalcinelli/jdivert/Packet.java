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
import com.github.ffalcinelli.jdivert.headers.*;
import com.github.ffalcinelli.jdivert.windivert.WinDivertAddress;
import com.github.ffalcinelli.jdivert.windivert.WinDivertDLL;
import com.sun.jna.Memory;

import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;

import static com.github.ffalcinelli.jdivert.Enums.Direction;
import static com.github.ffalcinelli.jdivert.Util.printHexBinary;
import static com.github.ffalcinelli.jdivert.exceptions.WinDivertException.throwExceptionOnGetLastError;
import static com.sun.jna.platform.win32.WinDef.UINT;
import static com.sun.jna.platform.win32.WinDef.USHORT;

/**
 * A single packet, possibly including an {@link com.github.ffalcinelli.jdivert.headers.Ip} header,
 * a {@link com.github.ffalcinelli.jdivert.headers.Tcp}/{@link com.github.ffalcinelli.jdivert.headers.Udp} header and a payload.
 * <p>
 * Creation of packets is cheap, attributes are parsed when accessing them.
 * </p>
 * Created by fabio on 21/10/2016.
 */
public class Packet {

    private ByteBuffer raw;
    private Direction direction;
    private int[] iface;
    private Transport transHdr;
    private Ip ipHdr;
    private Icmp icmpHdr;

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
     * @param direction The {@link Enums.Direction Direction}
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
            if (header instanceof Ip) {
                ipHdr = (Ip) header;
            } else if (header instanceof Icmp) {
                icmpHdr = (Icmp) header;
            } else {
                transHdr = (Transport) header;
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
     * Convenience method to check if the packet is {@link Enums.Direction#OUTBOUND OUTBOUND}
     *
     * @return True if packet is {@link Enums.Direction#OUTBOUND OUTBOUND}, false otherwise
     */
    public boolean isOutbound() {
        return direction == Direction.OUTBOUND;
    }

    /**
     * Convenience method to check if the packet is {@link Enums.Direction#INBOUND INBOUND}
     *
     * @return True if packet is {@link Enums.Direction#INBOUND INBOUND}, false otherwise
     */

    public boolean isInbound() {
        return direction == Direction.INBOUND;
    }

    /**
     * Convenience method to check if the packet has a {@link com.github.ffalcinelli.jdivert.headers.Ipv4 Ip header version 4}
     *
     * @return True if packet is an Ipv4 one
     */
    public boolean isIpv4() {
        return ipHdr instanceof Ipv4;
    }

    /**
     * Convenience method to check if the packet has a {@link com.github.ffalcinelli.jdivert.headers.Ipv6 Ip header version 6}
     *
     * @return True if packet is an Ipv6 one
     */
    public boolean isIpv6() {
        return ipHdr instanceof Ipv6;
    }

    /**
     * Convenience method to check if the packet has a {@link com.github.ffalcinelli.jdivert.headers.Icmpv4 Icmp header version 4}
     *
     * @return True if packet is an Icmpv4 one
     */
    public boolean isIcmpv4() {
        return icmpHdr instanceof Icmpv4;
    }

    /**
     * Convenience method to check if the packet has a {@link com.github.ffalcinelli.jdivert.headers.Icmpv6 Icmp header version 6}
     *
     * @return True if packet is an Icmpv6 one
     */
    public boolean isIcmpv6() {
        return icmpHdr instanceof Icmpv6;
    }

    /**
     * Convenience method to check if the packet has a {@link com.github.ffalcinelli.jdivert.headers.Udp Udp header}
     *
     * @return True if packet is an Udp one
     */
    public boolean isUdp() {
        return transHdr instanceof Udp;
    }

    /**
     * Convenience method to check if the packet has a {@link com.github.ffalcinelli.jdivert.headers.Tcp Tcp header}
     *
     * @return True if packet is an Tcp one
     */
    public boolean isTcp() {
        return transHdr instanceof Tcp;
    }

    /**
     * Convenience method to get the {@link com.github.ffalcinelli.jdivert.headers.Tcp} if present
     *
     * @return The {@link com.github.ffalcinelli.jdivert.headers.Tcp} if present, {@code null} otherwise
     */
    public Tcp getTcp() {
        return isTcp() ? (Tcp) transHdr : null;
    }

    /**
     * Convenience method to get the {@link com.github.ffalcinelli.jdivert.headers.Udp} if present
     *
     * @return The {@link com.github.ffalcinelli.jdivert.headers.Udp} if present, {@code null} otherwise
     */
    public Udp getUdp() {
        return isUdp() ? (Udp) transHdr : null;
    }

    /**
     * Convenience method to get the {@link com.github.ffalcinelli.jdivert.headers.Icmpv4} if present
     *
     * @return The {@link com.github.ffalcinelli.jdivert.headers.Icmpv4} if present, {@code null} otherwise
     */
    public Icmpv4 getIcmpv4() {
        return isIcmpv4() ? (Icmpv4) icmpHdr : null;
    }

    /**
     * Convenience method to get the {@link com.github.ffalcinelli.jdivert.headers.Icmpv6} if present
     *
     * @return The {@link com.github.ffalcinelli.jdivert.headers.Icmpv6} if present, {@code null} otherwise
     */
    public Icmpv6 getIcmpv6() {
        return isIcmpv6() ? (Icmpv6) icmpHdr : null;
    }

    /**
     * Convenience method to get the {@link com.github.ffalcinelli.jdivert.headers.Ipv4} if present
     *
     * @return The {@link com.github.ffalcinelli.jdivert.headers.Ipv4} if present, {@code null} otherwise
     */
    public Ipv4 getIpv4() {
        return isIpv4() ? (Ipv4) ipHdr : null;
    }

    /**
     * Convenience method to get the {@link com.github.ffalcinelli.jdivert.headers.Ipv6} if present
     *
     * @return The {@link com.github.ffalcinelli.jdivert.headers.Ipv6} if present, {@code null} otherwise
     */
    public Ipv6 getIpv6() {
        return isIpv6() ? (Ipv6) ipHdr : null;
    }

    /**
     * Convenience method to get the String representing the source address
     *
     * @return The source address String
     */
    public String getSrcAddr() {
        return ipHdr.getSrcAddrStr();
    }

    /**
     * Convenience method to set the source address
     *
     * @param address The String representing the source address to set
     * @throws UnknownHostException Unlikely to be thrown...
     */
    public void setSrcAddr(String address) throws UnknownHostException {
        ipHdr.setSrcAddrStr(address);
    }

    /**
     * Convenience method to get the String representing the destination address
     *
     * @return The destination address String
     */
    public String getDstAddr() {
        return ipHdr.getDstAddrStr();
    }

    /**
     * Convenience method to set the destination address
     *
     * @param address The String representing the destination address to set
     * @throws UnknownHostException Unlikely to be thrown...
     */
    public void setDstAddr(String address) throws UnknownHostException {
        ipHdr.setDstAddrStr(address);
    }

    /**
     * Convenience method to get the source port number, if present.
     *
     * @return The source port number if present, {@code null} otherwise.
     */
    public Integer getSrcPort() {
        return transHdr != null ? transHdr.getSrcPort() : null;
    }

    /**
     * Convenience method to set the source port number
     *
     * @param port The port number to set for source service. If packet does not have such info an {@link java.lang.IllegalStateException} is thrown.
     */
    public void setSrcPort(int port) {
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
    public Integer getDstPort() {
        return transHdr != null ? transHdr.getDstPort() : null;
    }

    /**
     * Convenience method to set the destination port number
     *
     * @param port The port number to set for destination service. If packet does not have such info an {@link java.lang.IllegalStateException} is thrown.
     */
    public void setDstPort(int port) {
        if (transHdr != null)
            transHdr.setDstPort(port);
        else
            throw new IllegalStateException("A port number cannot be set");
    }

    /**
     * Get the {@link Packet} payload.
     *
     * @return The payload's array of bytes
     */
    public byte[] getPayload() {
        return Util.getBytesAtOffset(raw, getHeadersLength(), raw.capacity() - getHeadersLength());
    }

    public void setPayload(byte[] payload) {
        //TODO: adjust length!
        Util.setBytesAtOffset(raw, getHeadersLength(), payload.length, payload);
    }

    public int getHeadersLength() {
        return ipHdr.getHeaderLength() + (transHdr != null ? transHdr.getHeaderLength() : icmpHdr.getHeaderLength());
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
     * Recalculates the checksum fields matching the given {@link Enums.CalcChecksumsOption options}
     *
     * @param options Drive the recalculateChecksum function
     * @throws WinDivertException Whenever the DLL call sets a LastError different by 0 (Success) or 997 (Overlapped I/O
     *                            is in progress)
     */
    public void recalculateChecksum(Enums.CalcChecksumsOption... options) throws WinDivertException {
        int flags = 0;
        for (Enums.CalcChecksumsOption option : options) {
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
     *
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
        return String.format("Packet {%s, %s, direction=%s, iface=%s, raw=%s}"
                , ipHdr
                , transHdr != null ? transHdr : icmpHdr
                , direction
                , Arrays.toString(iface)
                , printHexBinary(getRaw())
        );
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Packet packet = (Packet) o;
        return Arrays.equals(getRaw(), packet.getRaw()) &&
                getWinDivertAddress().equals(packet.getWinDivertAddress());
    }


    @Override
    public int hashCode() {
        int result = Arrays.hashCode(getRaw());
        result = 31 * result + getWinDivertAddress().hashCode();
        return result;
    }

}
