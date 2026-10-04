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
import com.github.ffalcinelli.jdivert.windivert.NativeAdapter;
import com.github.ffalcinelli.jdivert.windivert.WinDivertAddress;
import org.junit.jupiter.api.Test;

import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests of {@link WinDivert#recvBatch} and {@link WinDivert#stream} against a fake adapter: they run
 * everywhere, without privileges.
 */
public class WinDivertBatchTestCase {

    private static final byte[] PACKET = Packet.builder().ipv4("10.0.0.1", "10.0.0.2").udp(1000, 53).build().getRaw();

    /** Serves queued packets; an empty queue behaves as a timeout. */
    static final class FakeAdapter implements NativeAdapter {
        final Deque<byte[]> queue = new ArrayDeque<>();
        final List<Integer> timeouts = new ArrayList<>();
        WinDivertException error;
        boolean open;

        @Override
        public Handle open(String filter, int layer, short priority, long flags) {
            open = true;
            return new Handle() {
                @Override
                public void close() {
                    open = false;
                }

                @Override
                public boolean isValid() {
                    return open;
                }
            };
        }

        @Override
        public int recv(Handle handle, Buffer buffer, WinDivertAddress address, int timeoutMs) throws WinDivertException {
            timeouts.add(timeoutMs);
            if (error != null) {
                throw error;
            }
            byte[] next = queue.poll();
            if (next == null) {
                return -1;
            }
            buffer.getByteBuffer().put(next);
            address.setOutbound(true);
            return next.length;
        }

        @Override
        public Buffer allocateBuffer(int size) {
            ByteBuffer bb = ByteBuffer.allocate(size);
            return new Buffer() {
                @Override
                public ByteBuffer getByteBuffer() {
                    return bb;
                }

                @Override
                public int capacity() {
                    return bb.capacity();
                }

                @Override
                public void close() {
                }
            };
        }

        // Not used by these tests.
        @Override
        public <T> WinDivertAsyncResult<T> recvAsync(Handle handle, int bufsize, WinDivertAsyncResult.ResultConverter<T> converter) {
            throw new UnsupportedOperationException();
        }

        @Override
        public int send(Handle handle, ByteBuffer packet, WinDivertAddress address) {
            throw new UnsupportedOperationException();
        }

        @Override
        public <T> WinDivertAsyncResult<T> sendAsync(Handle handle, ByteBuffer packet, WinDivertAddress address, WinDivertAsyncResult.ResultConverter<T> converter) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void shutdown(Handle handle, int how) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void setParam(Handle handle, int param, long value) {
            throw new UnsupportedOperationException();
        }

        @Override
        public long getParam(Handle handle, int param) {
            throw new UnsupportedOperationException();
        }

        @Override
        public int calcChecksums(byte[] packet, WinDivertAddress address, long flags) {
            return 1;
        }

        @Override
        public long hashPacket(byte[] packet, long seed) {
            throw new UnsupportedOperationException();
        }

        @Override
        public String formatMessage(int errorCode) {
            return "error " + errorCode;
        }

        @Override
        public int getLastError() {
            return 0;
        }
    }

    private final FakeAdapter adapter = new FakeAdapter();

    private WinDivert open() throws WinDivertException {
        return new WinDivert(adapter, "true", Enums.Layer.NETWORK, 0, Enums.Flag.DEFAULT).open();
    }

    @Test
    public void recvBatchStopsAtMaxPackets() throws WinDivertException {
        for (int i = 0; i < 3; i++) {
            adapter.queue.add(PACKET);
        }
        try (WinDivert w = open()) {
            List<Packet> packets = w.recvBatch(2, Duration.ofSeconds(2));
            assertEquals(2, packets.size());
            for (Packet p : packets) {
                assertArrayEquals(PACKET, p.getRaw());
                assertEquals(53, (int) p.getDstPort().get());
            }
            // Only the first receive waits.
            assertEquals(Arrays.asList(2000, 0), adapter.timeouts);
        }
    }

    @Test
    public void recvBatchTakesWhatIsQueued() throws WinDivertException {
        adapter.queue.add(PACKET);
        try (WinDivert w = open()) {
            assertEquals(1, w.recvBatch(10, null).size());
            assertEquals(Arrays.asList(-1, 0), adapter.timeouts);
        }
    }

    @Test
    public void recvBatchTimesOutEmpty() throws WinDivertException {
        try (WinDivert w = open()) {
            assertTrue(w.recvBatch(5, Duration.ZERO).isEmpty());
            assertTrue(w.recvBatch(5, Duration.ofMillis(-5)).isEmpty());
            assertEquals(Arrays.asList(0, 0), adapter.timeouts);
        }
    }

    @Test
    public void recvBatchRejectsNonPositiveCount() throws WinDivertException {
        try (WinDivert w = open()) {
            assertThrows(IllegalArgumentException.class, () -> w.recvBatch(0, Duration.ZERO));
        }
    }

    @Test
    public void streamWrapsReceiveErrors() throws WinDivertException {
        adapter.queue.add(PACKET);
        try (WinDivert w = open()) {
            assertEquals(53, (int) w.stream().findFirst().get().getDstPort().get());
            adapter.error = new WinDivertException(5, "boom");
            UncheckedIOException e = assertThrows(UncheckedIOException.class, () -> w.stream().findFirst());
            assertTrue(e.getCause().getCause() instanceof WinDivertException);
        }
    }
}
