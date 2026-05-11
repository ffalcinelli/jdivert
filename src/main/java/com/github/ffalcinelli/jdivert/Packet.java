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

import com.github.ffalcinelli.jdivert.headers.Header;
import com.github.ffalcinelli.jdivert.headers.Icmp;
import com.github.ffalcinelli.jdivert.headers.Icmpv4;
import com.github.ffalcinelli.jdivert.headers.Icmpv6;
import com.github.ffalcinelli.jdivert.headers.Ip;
import com.github.ffalcinelli.jdivert.headers.Ipv4;
import com.github.ffalcinelli.jdivert.headers.Ipv6;
import com.github.ffalcinelli.jdivert.headers.Tcp;
import com.github.ffalcinelli.jdivert.headers.Transport;
import com.github.ffalcinelli.jdivert.headers.Udp;
import com.github.ffalcinelli.jdivert.windivert.NativeAdapterFactory;
import com.github.ffalcinelli.jdivert.windivert.WinDivertAddress;

import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.Optional;

import static com.github.ffalcinelli.jdivert.Util.printHexBinary;

/**
 * Represents a network packet and provides methods for accessing and modifying its fields.
 * <p>
 * A {@code Packet} consists of a sequence of headers (IP, Transport, ICMP) followed by an
 * optional payload. JDivert automatically parses these headers upon packet creation
 * directly from an underlying {@link java.nio.ByteBuffer} (typically a direct buffer).
 * </p>
 * <h3>Modification</h3>
 * <p>
 * This class provides high-level setters for both header fields and the payload. When the
 * payload is modified via {@link #setPayload(byte[])}, JDivert:
 * </p>
 * <ul>
 *     <li>Updates the underlying buffer in-place if the new payload fits.</li>
 *     <li>Reallocates the buffer only if the new payload exceeds the current capacity.</li>
 *     <li>Updates the IP Total Length and Transport layer (TCP/UDP) length fields.</li>
 * </ul>
 * <p>
 * This design ensures optimal performance by minimizing memory copying and allocations
 * during the packet processing lifecycle.
 * </p>
 * <p>
 * Note that checksums are typically recalculated by the WinDivert driver itself during
 * {@link WinDivert#send(Packet)} unless configured otherwise.
 * </p>
 */
public class Packet {

    private ByteBuffer raw;
    private final WinDivertAddress addr;
    private Ip ipHdr;
    private Transport transHdr;
    private Icmp icmpHdr;

    /**
     * Create a new Packet based upon the given raw bytes and {@link com.github.ffalcinelli.jdivert.windivert.WinDivertAddress address}.
     *
     * @param rawBytes The raw bytes of the packet.
     * @param addr     The {@link com.github.ffalcinelli.jdivert.windivert.WinDivertAddress address} associated with the packet.
     */
    public Packet(byte[] rawBytes, WinDivertAddress addr) {
        this(ByteBuffer.wrap(rawBytes), addr);
    }

    /**
     * Create a new Packet based upon the given {@link ByteBuffer} and {@link com.github.ffalcinelli.jdivert.windivert.WinDivertAddress address}.
     *
     * @param raw  The {@link ByteBuffer} containing the packet data.
     * @param addr The {@link com.github.ffalcinelli.jdivert.windivert.WinDivertAddress address} associated with the packet.
     */
    public Packet(ByteBuffer raw, WinDivertAddress addr) {
        this.raw = raw;
        this.raw.order(ByteOrder.BIG_ENDIAN);
        this.addr = addr;
        parse();
    }

    /**
     * Legacy constructor for tests.
     */
    public Packet(byte[] raw, int[] iface, Enums.Direction direction) {
        if (iface.length != 2) {
            throw new IllegalArgumentException("Iface parameter must be a IfIdx, IfSubIdx pair");
        }
        this.raw = ByteBuffer.wrap(raw);
        this.raw.order(ByteOrder.BIG_ENDIAN);
        this.addr = new WinDivertAddress();
        this.addr.setLayer(0);
        this.addr.Union.Network.IfIdx = iface[0];
        this.addr.Union.Network.SubIfIdx = iface[1];
        this.addr.setOutbound(direction == Enums.Direction.OUTBOUND);
        parse();
    }

    private void parse() {
        ipHdr = null;
        transHdr = null;
        icmpHdr = null;
        Header[] headers = Header.buildHeaders(raw);
        if (headers.length > 0 && headers[0] instanceof Ip) {
            ipHdr = (Ip) headers[0];
        }
        if (headers.length > 1) {
            if (headers[1] instanceof Transport) {
                transHdr = (Transport) headers[1];
            } else if (headers[1] instanceof Icmp) {
                icmpHdr = (Icmp) headers[1];
            }
        }
    }

    /**
     * Return the {@link java.nio.ByteBuffer} used to construct this packet.
     *
     * @return The internal {@link java.nio.ByteBuffer}.
     */
    public ByteBuffer getByteBuffer() {
        return raw;
    }

    public boolean isIpv4() {
        return ipHdr instanceof Ipv4;
    }

    public boolean isIpv6() {
        return ipHdr instanceof Ipv6;
    }

    public boolean isTcp() {
        return transHdr instanceof Tcp;
    }

    public boolean isUdp() {
        return transHdr instanceof Udp;
    }

    public boolean isIcmpv4() {
        return icmpHdr instanceof Icmpv4;
    }

    public boolean isIcmpv6() {
        return icmpHdr instanceof Icmpv6;
    }

    public boolean isOutbound() {
        return addr.isOutbound();
    }

    public boolean isInbound() {
        return !addr.isOutbound();
    }

    public boolean isLoopback() {
        return addr.isLoopback() || (addr.getLayer() == 0 && addr.Union.Network.IfIdx == 1);
    }

    public Optional<Ipv4> getIpv4() {
        return Optional.ofNullable(isIpv4() ? (Ipv4) ipHdr : null);
    }

    public Optional<Ipv6> getIpv6() {
        return Optional.ofNullable(isIpv6() ? (Ipv6) ipHdr : null);
    }

    public Optional<Tcp> getTcp() {
        return Optional.ofNullable(isTcp() ? (Tcp) transHdr : null);
    }

    public Optional<Udp> getUdp() {
        return Optional.ofNullable(isUdp() ? (Udp) transHdr : null);
    }

    public Optional<Icmpv4> getIcmpv4() {
        return Optional.ofNullable(isIcmpv4() ? (Icmpv4) icmpHdr : null);
    }

    public Optional<Icmpv6> getIcmpv6() {
        return Optional.ofNullable(isIcmpv6() ? (Icmpv6) icmpHdr : null);
    }

    public Optional<String> getSrcAddr() {
        return Optional.ofNullable(ipHdr != null ? ipHdr.getSrcAddrStr() : null);
    }

    public void setSrcAddr(String address) throws UnknownHostException {
        if (ipHdr != null) ipHdr.setSrcAddrStr(address);
    }

    public Optional<String> getDstAddr() {
        return Optional.ofNullable(ipHdr != null ? ipHdr.getDstAddrStr() : null);
    }

    public void setDstAddr(String address) throws UnknownHostException {
        if (ipHdr != null) ipHdr.setDstAddrStr(address);
    }

    public Optional<Integer> getSrcPort() {
        return Optional.ofNullable(transHdr != null ? transHdr.getSrcPort() : null);
    }

    public void setSrcPort(int port) {
        if (transHdr != null) transHdr.setSrcPort(port);
        else throw new IllegalStateException("No transport header");
    }

    public Optional<Integer> getDstPort() {
        return Optional.ofNullable(transHdr != null ? transHdr.getDstPort() : null);
    }

    public void setDstPort(int port) {
        if (transHdr != null) transHdr.setDstPort(port);
        else throw new IllegalStateException("No transport header");
    }

    /**
     * Get the IP header of the packet.
     *
     * @return An {@link Optional} containing the {@link com.github.ffalcinelli.jdivert.headers.Ip IP header} if present.
     */
    public Optional<Ip> getIp() {
        return Optional.ofNullable(ipHdr);
    }

    /**
     * Get the transport header of the packet.
     *
     * @return An {@link Optional} containing the {@link com.github.ffalcinelli.jdivert.headers.Transport transport header} (TCP, UDP) if present.
     */
    public Optional<Transport> getTransport() {
        return Optional.ofNullable(transHdr);
    }

    /**
     * Get the ICMP header of the packet.
     *
     * @return An {@link Optional} containing the {@link com.github.ffalcinelli.jdivert.headers.Icmp ICMP header} if present.
     */
    public Optional<Icmp> getIcmp() {
        return Optional.ofNullable(icmpHdr);
    }

    /**
     * Get the payload of the packet.
     *
     * @return The payload of the packet as a byte array.
     */
    public byte[] getPayload() {
        int headersLength = getHeadersLength();
        return Util.getBytesAtOffset(raw, headersLength, raw.limit() - headersLength);
    }

    /**
     * Replaces the current packet payload with a new one.
     * <p>
     * This method automatically handles buffer reallocation if the new payload has a different
     * size than the original. It also updates the IP layer's Total Length and the Transport
     * layer's (TCP/UDP) Length fields to maintain consistency.
     * </p>
     *
     * @param payload The new payload as a byte array.
     */
    public void setPayload(byte[] payload) {
        int headersLength = getHeadersLength();
        int newTotalLength = headersLength + payload.length;

        if (newTotalLength <= raw.capacity()) {
            Util.setBytesAtOffset(raw, headersLength, payload.length, payload);
            raw.limit(newTotalLength);
        } else {
            byte[] newRaw = new byte[newTotalLength];
            byte[] headerBytes = Util.getBytesAtOffset(raw, 0, headersLength);
            System.arraycopy(headerBytes, 0, newRaw, 0, headersLength);
            System.arraycopy(payload, 0, newRaw, headersLength, payload.length);
            this.raw = ByteBuffer.wrap(newRaw);
            this.raw.order(ByteOrder.BIG_ENDIAN);
            parse();
        }

        if (ipHdr instanceof Ipv4) {
            ((Ipv4) ipHdr).setTotalLength(newTotalLength);
        } else if (ipHdr instanceof Ipv6) {
            ((Ipv6) ipHdr).setPayloadLength((short) (newTotalLength - 40));
        }

        if (transHdr instanceof Udp) {
            ((Udp) transHdr).setLength(payload.length + transHdr.getHeaderLength());
        }
    }

    /**
     * Overall {@link Packet}'s header length.
     *
     * @return The overall {@link Packet} headers length
     */
    public int getHeadersLength() {
        return (ipHdr != null ? ipHdr.getHeaderLength() : 0) + (transHdr != null ? transHdr.getHeaderLength() : (icmpHdr != null ? icmpHdr.getHeaderLength() : 0));
    }

    /**
     * Get the raw bytes of the packet.
     *
     * @return The raw bytes of the packet.
     */
    public byte[] getRaw() {
        return Util.getBytesAtOffset(raw, 0, raw.limit());
    }

    /**
     * Get the {@link com.github.ffalcinelli.jdivert.windivert.WinDivertAddress address} associated with the packet.
     *
     * @return The {@link com.github.ffalcinelli.jdivert.windivert.WinDivertAddress address} of the packet.
     */
    public WinDivertAddress getWinDivertAddress() {
        return addr;
    }

    /**
     * Recalculate the checksums of the packet headers.
     *
     * @param options A set of {@link Enums.CalcChecksumsOption options} to use when recalculating checksums.
     */
    public void recalculateChecksum(Enums.CalcChecksumsOption... options) {
        long flags = 0;
        for (Enums.CalcChecksumsOption option : options) {
            flags |= option.getValue();
        }
        byte[] rawBytes = getRaw();
        NativeAdapterFactory.getAdapter().calcChecksums(rawBytes, addr, flags);
        this.raw = ByteBuffer.wrap(rawBytes);
        this.raw.order(ByteOrder.BIG_ENDIAN);
        parse();
    }

    @Override
    public String toString() {
        return String.format("Packet {%s, %s, raw=%s}"
                , ipHdr
                , transHdr != null ? transHdr : icmpHdr
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
