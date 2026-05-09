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

import com.sun.jna.Structure;
import com.sun.jna.Union;

import java.util.Arrays;
import java.util.List;

/**
 * Represents the "address" of a captured or injected packet.
 */
public class WinDivertAddress extends Structure {
    public long Timestamp;
    public int bitfield1;
    public int Reserved2;
    public WinDivertData Union;

    public static class WinDivertData extends Union {
        public NetworkData Network;
        public FlowData Flow;
        public SocketData Socket;
        public ReflectData Reflect;
        public byte[] Reserved3 = new byte[64];
        
        public WinDivertData() {
        }
        
        public static class NetworkData extends Structure {
            public int IfIdx;
            public int SubIfIdx;
            
            @Override
            protected List<String> getFieldOrder() {
                return Arrays.asList("IfIdx", "SubIfIdx");
            }
        }
        
        public static class FlowData extends Structure {
            public long EndpointId;
            public long ParentEndpointId;
            public int ProcessId;
            public int[] LocalAddr = new int[4];
            public int[] RemoteAddr = new int[4];
            public short LocalPort;
            public short RemotePort;
            public byte Protocol;
            
            @Override
            protected List<String> getFieldOrder() {
                return Arrays.asList("EndpointId", "ParentEndpointId", "ProcessId", "LocalAddr", "RemoteAddr", "LocalPort", "RemotePort", "Protocol");
            }
        }
        
        public static class SocketData extends Structure {
            public long EndpointId;
            public long ParentEndpointId;
            public int ProcessId;
            public int[] LocalAddr = new int[4];
            public int[] RemoteAddr = new int[4];
            public short LocalPort;
            public short RemotePort;
            public byte Protocol;
            
            @Override
            protected List<String> getFieldOrder() {
                return Arrays.asList("EndpointId", "ParentEndpointId", "ProcessId", "LocalAddr", "RemoteAddr", "LocalPort", "RemotePort", "Protocol");
            }
        }
        
        public static class ReflectData extends Structure {
            public long Timestamp;
            public int ProcessId;
            public int Layer;
            public long Flags;
            public short Priority;
            
            @Override
            protected List<String> getFieldOrder() {
                return Arrays.asList("Timestamp", "ProcessId", "Layer", "Flags", "Priority");
            }
        }
    }

    public WinDivertAddress() {
        Union = new WinDivertData();
        Union.setType(WinDivertData.NetworkData.class);
    }

    @Override
    protected List<String> getFieldOrder() {
        return Arrays.asList("Timestamp", "bitfield1", "Reserved2", "Union");
    }

    @Override
    public void read() {
        super.read();
        int layer = getLayer();
        switch (layer) {
            case 0: // NETWORK
            case 1: // NETWORK_FORWARD
                Union.setType(WinDivertData.NetworkData.class);
                break;
            case 2: // FLOW
                Union.setType(WinDivertData.FlowData.class);
                break;
            case 3: // SOCKET
                Union.setType(WinDivertData.SocketData.class);
                break;
            case 4: // REFLECT
                Union.setType(WinDivertData.ReflectData.class);
                break;
            default:
                Union.setType(byte[].class);
        }
        Union.read();
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
        return false;
    }

    @Override
    public int hashCode() {
        if (getLayer() == 0) {
            int result = 31 * Union.Network.IfIdx;
            result = 31 * result + Union.Network.SubIfIdx;
            result = 31 * result + (isOutbound() ? 1 : 0);
            return result;
        }
        return super.hashCode();
    }
}
