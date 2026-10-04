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
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link WinDivert#recvBatch} against the real backend (WinDivert, or libebpfdivert as root).
 */
@EnabledIf("com.github.ffalcinelli.jdivert.CaptureCondition#canCapture")
public class RecvBatchLiveTestCase {

    @Test
    public void receivesQueuedPackets() throws IOException, WinDivertException {
        try (DatagramSocket socket = new DatagramSocket(0, InetAddress.getLoopbackAddress())) {
            int port = socket.getLocalPort();
            try (WinDivert w = new WinDivert("loopback and udp.DstPort == " + port, Enums.Layer.NETWORK, 0,
                    Enums.Flag.SNIFF).open()) {
                byte[] data = "batch".getBytes();
                for (int i = 0; i < 3; i++) {
                    socket.send(new DatagramPacket(data, data.length, InetAddress.getLoopbackAddress(), port));
                }
                // A batch only takes what is queued already, so the three packets may take several batches.
                List<Packet> packets = new ArrayList<>();
                long deadline = System.nanoTime() + Duration.ofSeconds(10).toNanos();
                while (packets.size() < 3 && System.nanoTime() < deadline) {
                    packets.addAll(w.recvBatch(3 - packets.size(), Duration.ofSeconds(2)));
                }
                assertEquals(3, packets.size());
                for (Packet p : packets) {
                    assertEquals(port, (int) p.getDstPort().get());
                }
            }
        }
    }

    @Test
    public void timesOutEmpty() throws WinDivertException {
        try (WinDivert w = new WinDivert("loopback and udp.DstPort == 1", Enums.Layer.NETWORK, 0,
                Enums.Flag.SNIFF).open()) {
            long start = System.nanoTime();
            List<Packet> packets = w.recvBatch(5, Duration.ofMillis(200));
            long elapsedMs = Duration.ofNanos(System.nanoTime() - start).toMillis();
            assertTrue(packets.isEmpty());
            assertTrue(elapsedMs >= 150 && elapsedMs < 5000, "waited " + elapsedMs + " ms");
            assertTrue(w.recvBatch(5, Duration.ZERO).isEmpty());
        }
    }
}
