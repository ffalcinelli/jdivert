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

import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import static org.junit.jupiter.api.Assertions.*;

public class AddressCodecTestCase {

    @Test
    public void networkRoundTripKeepsOpaqueUnionBytes() {
        byte[] raw = new byte[AddressCodec.SIZE];
        ByteBuffer b = ByteBuffer.wrap(raw).order(ByteOrder.LITTLE_ENDIAN);
        b.putLong(0, 123456789L);
        b.putInt(8, (1 << 17) | (1 << 20)); // layer 0, outbound, ipv6
        b.putInt(16, 7);                     // IfIdx
        b.putInt(20, 3);                     // SubIfIdx
        for (int i = 32; i < 80; i++) {
            raw[i] = (byte) i;               // backend-private context
        }

        WinDivertAddress a = new WinDivertAddress();
        AddressCodec.decode(ByteBuffer.wrap(raw), a);
        assertEquals(123456789L, a.Timestamp);
        assertTrue(a.isOutbound());
        assertTrue(a.isIPv6());
        assertEquals(7, a.Union.Network.IfIdx);
        assertEquals(3, a.Union.Network.SubIfIdx);

        a.Union.Network.IfIdx = 9;
        byte[] out = AddressCodec.encode(a);
        assertEquals(9, ByteBuffer.wrap(out).order(ByteOrder.LITTLE_ENDIAN).getInt(16));
        for (int i = 32; i < 80; i++) {
            assertEquals((byte) i, out[i], "opaque byte " + i);
        }
    }

    @Test
    public void flowSocketReflectLayouts() {
        WinDivertAddress a = new WinDivertAddress();
        a.setLayer(2);
        a.Union.Flow.EndpointId = 42;
        a.Union.Flow.ProcessId = 1000;
        a.Union.Flow.LocalAddr[0] = 0x0100007f;
        a.Union.Flow.RemotePort = 443;
        a.Union.Flow.Protocol = 6;
        ByteBuffer b = ByteBuffer.wrap(AddressCodec.encode(a)).order(ByteOrder.LITTLE_ENDIAN);
        assertEquals(42, b.getLong(16));
        assertEquals(1000, b.getInt(32));
        assertEquals(0x0100007f, b.getInt(36));
        assertEquals(443, b.getShort(70));
        assertEquals(6, b.get(72));

        WinDivertAddress back = new WinDivertAddress();
        AddressCodec.decode(b, back);
        assertEquals(1000, back.Union.Flow.ProcessId);
        assertEquals(443, back.Union.Flow.RemotePort);

        WinDivertAddress r = new WinDivertAddress();
        r.setLayer(4);
        r.Union.Reflect.Priority = -5;
        r.Union.Reflect.Flags = 3;
        ByteBuffer rb = ByteBuffer.wrap(AddressCodec.encode(r)).order(ByteOrder.LITTLE_ENDIAN);
        assertEquals(3, rb.getLong(32));
        assertEquals(-5, rb.getShort(40));
    }

    @Test
    public void socketLayout() {
        WinDivertAddress a = new WinDivertAddress();
        a.setLayer(3);
        a.Union.Socket.EndpointId = 7;
        a.Union.Socket.ParentEndpointId = 8;
        a.Union.Socket.ProcessId = 2000;
        a.Union.Socket.LocalAddr[3] = 0x0100007f;
        a.Union.Socket.RemoteAddr[0] = 0x0200007f;
        a.Union.Socket.LocalPort = 8080;
        a.Union.Socket.RemotePort = 53;
        a.Union.Socket.Protocol = 17;
        ByteBuffer b = ByteBuffer.wrap(AddressCodec.encode(a)).order(ByteOrder.LITTLE_ENDIAN);
        assertEquals(7, b.getLong(16));
        assertEquals(8, b.getLong(24));
        assertEquals(2000, b.getInt(32));
        assertEquals(0x0100007f, b.getInt(48));
        assertEquals(0x0200007f, b.getInt(52));
        assertEquals(8080, b.getShort(68));
        assertEquals(53, b.getShort(70));
        assertEquals(17, b.get(72));

        WinDivertAddress back = new WinDivertAddress();
        AddressCodec.decode(b, back);
        assertEquals(2000, back.Union.Socket.ProcessId);
        assertEquals(0x0100007f, back.Union.Socket.LocalAddr[3]);
        assertEquals(8080, back.Union.Socket.LocalPort);
        assertEquals(17, back.Union.Socket.Protocol);
    }
}
