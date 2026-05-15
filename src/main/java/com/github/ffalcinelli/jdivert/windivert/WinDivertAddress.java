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

package com.github.ffalcinelli.jdivert.windivert;

import java.util.Objects;

/**
 * Represents the "address" of a captured or injected packet.
 */
public class WinDivertAddress {
    public long Timestamp;
    public int bitfield1;
    public int Reserved2;
    public WinDivertData Union = new WinDivertData();

    public static class WinDivertData {
        public NetworkData Network = new NetworkData();
        public FlowData Flow = new FlowData();
        public SocketData Socket = new SocketData();
        public ReflectData Reflect = new ReflectData();
        public byte[] Reserved3 = new byte[64];

        public static class NetworkData {
            public int IfIdx;
            public int SubIfIdx;
        }

        public static class FlowData {
            public long EndpointId;
            public long ParentEndpointId;
            public int ProcessId;
            public int[] LocalAddr = new int[4];
            public int[] RemoteAddr = new int[4];
            public short LocalPort;
            public short RemotePort;
            public byte Protocol;
        }

        public static class SocketData {
            public long EndpointId;
            public long ParentEndpointId;
            public int ProcessId;
            public int[] LocalAddr = new int[4];
            public int[] RemoteAddr = new int[4];
            public short LocalPort;
            public short RemotePort;
            public byte Protocol;
        }

        public static class ReflectData {
            public long Timestamp;
            public int ProcessId;
            public int Layer;
            public long Flags;
            public short Priority;
        }
    }

    public int getLayer() {
        return bitfield1 & 0xFF;
    }

    public void setLayer(int layer) {
        bitfield1 = (bitfield1 & ~0xFF) | (layer & 0xFF);
    }

    public int getEvent() {
        return (bitfield1 >> 8) & 0xFF;
    }

    public void setEvent(int event) {
        bitfield1 = (bitfield1 & ~(0xFF << 8)) | ((event & 0xFF) << 8);
    }

    public boolean isSniffed() {
        return ((bitfield1 >> 16) & 1) != 0;
    }

    public void setSniffed(boolean sniffed) {
        if (sniffed) bitfield1 |= (1 << 16);
        else bitfield1 &= ~(1 << 16);
    }

    public boolean isOutbound() {
        return ((bitfield1 >> 17) & 1) != 0;
    }

    public void setOutbound(boolean outbound) {
        if (outbound) bitfield1 |= (1 << 17);
        else bitfield1 &= ~(1 << 17);
    }

    public boolean isLoopback() {
        return ((bitfield1 >> 18) & 1) != 0;
    }

    public void setLoopback(boolean loopback) {
        if (loopback) bitfield1 |= (1 << 18);
        else bitfield1 &= ~(1 << 18);
    }

    public boolean isImpostor() {
        return ((bitfield1 >> 19) & 1) != 0;
    }

    public void setImpostor(boolean impostor) {
        if (impostor) bitfield1 |= (1 << 19);
        else bitfield1 &= ~(1 << 19);
    }

    public boolean isIPv6() {
        return ((bitfield1 >> 20) & 1) != 0;
    }

    public void setIPv6(boolean ipv6) {
        if (ipv6) bitfield1 |= (1 << 20);
        else bitfield1 &= ~(1 << 20);
    }

    public boolean hasIPChecksum() {
        return ((bitfield1 >> 21) & 1) != 0;
    }

    public void setIPChecksum(boolean ipChecksum) {
        if (ipChecksum) bitfield1 |= (1 << 21);
        else bitfield1 &= ~(1 << 21);
    }

    public boolean hasTCPChecksum() {
        return ((bitfield1 >> 22) & 1) != 0;
    }

    public void setTCPChecksum(boolean tcpChecksum) {
        if (tcpChecksum) bitfield1 |= (1 << 22);
        else bitfield1 &= ~(1 << 22);
    }

    public boolean hasUDPChecksum() {
        return ((bitfield1 >> 23) & 1) != 0;
    }

    public void setUDPChecksum(boolean udpChecksum) {
        if (udpChecksum) bitfield1 |= (1 << 23);
        else bitfield1 &= ~(1 << 23);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        WinDivertAddress that = (WinDivertAddress) o;
        if (getLayer() == 0 && that.getLayer() == 0) {
            return Union.Network.IfIdx == that.Union.Network.IfIdx &&
                    Union.Network.SubIfIdx == that.Union.Network.SubIfIdx &&
                    isOutbound() == that.isOutbound();
        }
        return bitfield1 == that.bitfield1 && Timestamp == that.Timestamp && Reserved2 == that.Reserved2;
    }

    @Override
    public int hashCode() {
        if (getLayer() == 0) {
            int result = 31 * Union.Network.IfIdx;
            result = 31 * result + Union.Network.SubIfIdx;
            result = 31 * result + (isOutbound() ? 1 : 0);
            return result;
        }
        return Objects.hash(Timestamp, bitfield1, Reserved2);
    }
}
