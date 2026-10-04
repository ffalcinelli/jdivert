/*
 * Copyright (c) Fabio Falcinelli 2026.
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

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Converts {@link WinDivertAddress} to and from the native 80-byte
 * {@code WINDIVERT_ADDRESS} layout (also {@code struct ebpfdivert_address}).
 * <p>
 * The whole 64-byte union is kept in {@link WinDivertAddress.WinDivertData#Reserved3} so that
 * opaque data a backend stores there survives a receive/send round trip: libebpfdivert uses it to
 * re-inject a packet exactly where it was captured. Layer-specific fields are decoded from it and
 * written back on top of it when encoding.
 */
public final class AddressCodec {

    /** Size of the native address structure. */
    public static final int SIZE = 80;
    private static final int UNION = 16;

    private AddressCodec() {
    }

    /**
     * Decodes a native address.
     *
     * @param src  buffer positioned at the address; its position is not changed
     * @param addr the address to fill
     */
    public static void decode(ByteBuffer src, WinDivertAddress addr) {
        ByteBuffer b = src.duplicate().order(ByteOrder.LITTLE_ENDIAN);
        int base = b.position();
        addr.Timestamp = b.getLong(base);
        addr.bitfield1 = b.getInt(base + 8);
        addr.Reserved2 = b.getInt(base + 12);
        b.position(base + UNION);
        b.get(addr.Union.Reserved3, 0, 64);

        int u = base + UNION;
        switch (addr.getLayer()) {
            case 0:
            case 1:
                addr.Union.Network.IfIdx = b.getInt(u);
                addr.Union.Network.SubIfIdx = b.getInt(u + 4);
                break;
            case 2: {
                WinDivertAddress.WinDivertData.FlowData f = addr.Union.Flow;
                f.EndpointId = b.getLong(u);
                f.ParentEndpointId = b.getLong(u + 8);
                f.ProcessId = b.getInt(u + 16);
                for (int i = 0; i < 4; i++) {
                    f.LocalAddr[i] = b.getInt(u + 20 + 4 * i);
                    f.RemoteAddr[i] = b.getInt(u + 36 + 4 * i);
                }
                f.LocalPort = b.getShort(u + 52);
                f.RemotePort = b.getShort(u + 54);
                f.Protocol = b.get(u + 56);
                break;
            }
            case 3: {
                WinDivertAddress.WinDivertData.SocketData s = addr.Union.Socket;
                s.EndpointId = b.getLong(u);
                s.ParentEndpointId = b.getLong(u + 8);
                s.ProcessId = b.getInt(u + 16);
                for (int i = 0; i < 4; i++) {
                    s.LocalAddr[i] = b.getInt(u + 20 + 4 * i);
                    s.RemoteAddr[i] = b.getInt(u + 36 + 4 * i);
                }
                s.LocalPort = b.getShort(u + 52);
                s.RemotePort = b.getShort(u + 54);
                s.Protocol = b.get(u + 56);
                break;
            }
            case 4: {
                WinDivertAddress.WinDivertData.ReflectData r = addr.Union.Reflect;
                r.Timestamp = b.getLong(u);
                r.ProcessId = b.getInt(u + 8);
                r.Layer = b.getInt(u + 12);
                r.Flags = b.getLong(u + 16);
                r.Priority = b.getShort(u + 24);
                break;
            }
            default:
                break;
        }
    }

    /**
     * Encodes an address.
     *
     * @param addr the address
     * @param dst  buffer positioned where the 80 bytes go; its position is not changed
     */
    public static void encode(WinDivertAddress addr, ByteBuffer dst) {
        ByteBuffer b = dst.duplicate().order(ByteOrder.LITTLE_ENDIAN);
        int base = b.position();
        b.putLong(base, addr.Timestamp);
        b.putInt(base + 8, addr.bitfield1);
        b.putInt(base + 12, addr.Reserved2);
        b.position(base + UNION);
        b.put(addr.Union.Reserved3, 0, 64);

        int u = base + UNION;
        switch (addr.getLayer()) {
            case 0:
            case 1:
                b.putInt(u, addr.Union.Network.IfIdx);
                b.putInt(u + 4, addr.Union.Network.SubIfIdx);
                break;
            case 2: {
                WinDivertAddress.WinDivertData.FlowData f = addr.Union.Flow;
                b.putLong(u, f.EndpointId);
                b.putLong(u + 8, f.ParentEndpointId);
                b.putInt(u + 16, f.ProcessId);
                for (int i = 0; i < 4; i++) {
                    b.putInt(u + 20 + 4 * i, f.LocalAddr[i]);
                    b.putInt(u + 36 + 4 * i, f.RemoteAddr[i]);
                }
                b.putShort(u + 52, f.LocalPort);
                b.putShort(u + 54, f.RemotePort);
                b.put(u + 56, f.Protocol);
                break;
            }
            case 3: {
                WinDivertAddress.WinDivertData.SocketData s = addr.Union.Socket;
                b.putLong(u, s.EndpointId);
                b.putLong(u + 8, s.ParentEndpointId);
                b.putInt(u + 16, s.ProcessId);
                for (int i = 0; i < 4; i++) {
                    b.putInt(u + 20 + 4 * i, s.LocalAddr[i]);
                    b.putInt(u + 36 + 4 * i, s.RemoteAddr[i]);
                }
                b.putShort(u + 52, s.LocalPort);
                b.putShort(u + 54, s.RemotePort);
                b.put(u + 56, s.Protocol);
                break;
            }
            case 4: {
                WinDivertAddress.WinDivertData.ReflectData r = addr.Union.Reflect;
                b.putLong(u, r.Timestamp);
                b.putInt(u + 8, r.ProcessId);
                b.putInt(u + 12, r.Layer);
                b.putLong(u + 16, r.Flags);
                b.putShort(u + 24, r.Priority);
                break;
            }
            default:
                break;
        }
    }

    /**
     * Encodes an address into a new little-endian buffer of {@link #SIZE} bytes.
     *
     * @param addr the address
     * @return the encoded bytes
     */
    public static byte[] encode(WinDivertAddress addr) {
        byte[] out = new byte[SIZE];
        encode(addr, ByteBuffer.wrap(out));
        return out;
    }
}
