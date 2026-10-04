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

package com.github.ffalcinelli.jdivert;

import com.github.ffalcinelli.jdivert.exceptions.WinDivertException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;

import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;

import static org.junit.jupiter.api.Assertions.*;

/**
 * FLOW, SOCKET and REFLECT layers with real sockets (WinDivert on Windows,
 * libebpfdivert on Linux as root).
 */
@EnabledIf("com.github.ffalcinelli.jdivert.CaptureCondition#canCapture")
public class EventLayersTestCase {

    private static Packet recvWithin(WinDivert w, long millis) throws Exception {
        WinDivertAsyncResult<Packet> r = w.recvAsync();
        long deadline = System.currentTimeMillis() + millis;
        while (!r.isCompleted() && System.currentTimeMillis() < deadline) {
            Thread.sleep(10);
        }
        return r.isCompleted() ? r.get() : null;
    }

    @Test
    public void socketConnectEvent() throws Exception {
        try (ServerSocket server = new ServerSocket(12380, 1, InetAddress.getLoopbackAddress());
             WinDivert w = new WinDivert("event == CONNECT and remotePort == 12380", Enums.Layer.SOCKET, 0,
                     Enums.Flag.SNIFF, Enums.Flag.RECV_ONLY).open()) {
            try (Socket client = new Socket(InetAddress.getLoopbackAddress(), 12380)) {
                assertTrue(client.isConnected());
            }
            Packet p = recvWithin(w, 3000);
            assertNotNull(p, "no CONNECT event");
            assertEquals(Enums.Layer.SOCKET.getValue(), p.getWinDivertAddress().getLayer());
            assertEquals(4, p.getWinDivertAddress().getEvent()); // SOCKET_CONNECT
            assertEquals(12380, p.getWinDivertAddress().Union.Socket.RemotePort & 0xFFFF);
            assertEquals(6, p.getWinDivertAddress().Union.Socket.Protocol);
        }
    }

    @Test
    public void socketConnectBlocked() throws Exception {
        try (ServerSocket server = new ServerSocket(12381, 1, InetAddress.getLoopbackAddress());
             WinDivert w = new WinDivert("event == CONNECT and remotePort == 12381", Enums.Layer.SOCKET, 0,
                     Enums.Flag.RECV_ONLY).open()) {
            assertThrows(IOException.class, () -> new Socket(InetAddress.getLoopbackAddress(), 12381).close());
        }
        try (ServerSocket server = new ServerSocket(12381, 1, InetAddress.getLoopbackAddress());
             Socket client = new Socket(InetAddress.getLoopbackAddress(), 12381)) {
            assertTrue(client.isConnected(), "connect must work again once the handle is closed");
        }
    }

    @Test
    public void flowEstablished() throws Exception {
        try (ServerSocket server = new ServerSocket(12382, 1, InetAddress.getLoopbackAddress());
             WinDivert w = new WinDivert("remotePort == 12382", Enums.Layer.FLOW, 0,
                     Enums.Flag.SNIFF, Enums.Flag.RECV_ONLY).open()) {
            try (Socket client = new Socket(InetAddress.getLoopbackAddress(), 12382);
                 Socket accepted = server.accept()) {
                assertTrue(accepted.isConnected());
            }
            Packet p = recvWithin(w, 3000);
            assertNotNull(p, "no FLOW event");
            assertEquals(Enums.Layer.FLOW.getValue(), p.getWinDivertAddress().getLayer());
            assertEquals(1, p.getWinDivertAddress().getEvent()); // FLOW_ESTABLISHED
        }
    }

    @Test
    public void reflectSeesOtherHandles() throws Exception {
        try (WinDivert reflect = new WinDivert("event == OPEN and priority == 1234", Enums.Layer.REFLECT, 0,
                Enums.Flag.SNIFF, Enums.Flag.RECV_ONLY).open();
             WinDivert other = new WinDivert("false", Enums.Layer.NETWORK, 1234).open()) {
            Packet p = recvWithin(reflect, 3000);
            assertNotNull(p, "no REFLECT event");
            assertEquals(1234, p.getWinDivertAddress().Union.Reflect.Priority);
            assertEquals(0, p.getWinDivertAddress().Union.Reflect.Layer);
        }
    }

    @Test
    public void flagsAreValidatedLikeWinDivert() {
        assertThrows(WinDivertException.class,
                () -> new WinDivert("true", Enums.Layer.FLOW, 0, Enums.Flag.RECV_ONLY).open().close());
    }
}
