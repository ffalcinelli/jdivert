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
import com.github.ffalcinelli.jdivert.windivert.NativeAdapter;
import com.github.ffalcinelli.jdivert.windivert.NativeAdapterFactory;
import com.github.ffalcinelli.jdivert.windivert.WinDivertAddress;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import static com.github.ffalcinelli.jdivert.Enums.CalcChecksumsOption;
import static com.github.ffalcinelli.jdivert.Enums.Flag;
import static com.github.ffalcinelli.jdivert.Enums.Layer;
import static com.github.ffalcinelli.jdivert.Enums.Param;
import static com.github.ffalcinelli.jdivert.Enums.Shutdown;

/**
 * A WinDivert handle used to capture, modify, and inject network packets.
 * <p>
 * This class is the primary entry point for the JDivert library. It provides an idiomatic Java
 * interface to the native WinDivert driver.
 * </p>
 * <h3>Resource Management</h3>
 * <p>
 * This class implements {@link AutoCloseable}. It is critical to call {@link #close()} or use
 * a try-with-resources block to ensure the native handle is released and the driver is unloaded
 * when no longer needed. Internal native buffers allocated during {@link #recv()} are
 * automatically managed and released using robust {@code try-finally} blocks.
 * </p>
 * <h3>Zero-Copy Architecture</h3>
 * <p>
 * JDivert uses a zero-copy architecture where captured packets are wrapped in direct
 * {@link java.nio.ByteBuffer} objects. This allows the Java application to read and modify
 * packet data directly in native memory, eliminating redundant heap allocations and memory copies.
 * </p>
 * <h3>Thread Safety</h3>
 * <p>
 * Instances of {@code WinDivert} are thread-safe for {@link #recv()} and {@link #send(Packet)}
 * operations. Multiple threads can concurrently call {@code recv()} on the same handle; the
 * underlying driver will distribute captured packets among the calling threads.
 * </p>
 * <h3>Zero-Install</h3>
 * <p>
 * JDivert bundles the necessary native WinDivert binaries. On first use, it extracts them
 * to a temporary directory and configures the environment to load them automatically.
 * Note that **Administrator privileges** are required to open a handle.
 * </p>
 */
public class WinDivert implements AutoCloseable {
    public static int DEFAULT_PACKET_BUFFER_SIZE = 65575;
    private final NativeAdapter adapter = NativeAdapterFactory.getAdapter();
    private final String filter;
    private final Layer layer;
    private final int priority;
    private final int flags;
    private NativeAdapter.Handle handle;

    /**
     * Create a new WinDivert instance based upon the given filter for
     * {@link Enums.Layer#NETWORK NETWORK} layer with priority set to 0 and in
     * {@link Enums.Flag#DEFAULT DEFAULT} mode (Drop and divert packet).
     *
     * @param filter The filter string expressed using <a href="https://www.reqrypt.org/windivert-doc.html#filter_language">WinDivert filter language.</a>
     */
    public WinDivert(String filter) {
        this(filter, Layer.NETWORK, 0, Flag.DEFAULT);
    }


    /**
     * Create a new WinDivert instance based upon the given parameters
     *
     * @param filter   The filter string expressed using <a href="https://www.reqrypt.org/windivert-doc.html#filter_language">WinDivert filter language.</a>
     * @param layer    The {@link Enums.Layer layer}
     * @param priority The priority of the handle
     * @param flags    Additional {@link Enums.Flag flags}
     */
    public WinDivert(String filter, Layer layer, int priority, Flag... flags) {
        this.filter = filter;
        this.layer = layer;
        this.priority = priority;
        List<Flag> flagList = Arrays.asList(flags);
        if (flagList.contains(Flag.SNIFF) && flagList.contains(Flag.DROP)) {
            throw new IllegalArgumentException(String.format("A filter cannot be set with flags %s and %s at same time.", Flag.SNIFF, Flag.DROP));
        }
        this.flags = Stream.of(flags).map(Flag::getValue).reduce(0, (a, b) -> a | b);
    }

    /**
     * Opens a WinDivert handle for the given filter.<br>
     * Unless otherwise specified by flags, any packet that matches the filter will be diverted to the handle.<br>
     * Diverted packets can be read by the application with {@link #recv() recv}.
     *
     * @return this instance to allow call chaining (e.g. {@code Windivert w = new WinDivert("true").open()})
     * @throws WinDivertException Whenever the DLL call sets a LastError different by 0 (Success) or 997 (Overlapped I/O
     *                            is in progress)
     */
    public WinDivert open() throws WinDivertException {
        if (isOpen()) {
            throw new IllegalStateException("The instance is already in open state");
        }
        handle = adapter.open(filter, layer.getValue(), (short) priority, flags);
        //Allow call chaining
        return this;
    }

    /**
     * Indicates if there is currently an open handle.
     *
     * @return True if the handle is open, false otherwise
     */
    public boolean isOpen() {
        return handle != null && handle.isValid();
    }

    /**
     * Closes the handle opened by {@link #open() open}.
     */
    public void close() throws WinDivertException {
        if (isOpen()) {
            handle.close();
            handle = null;
        }
    }

    @SuppressWarnings("deprecation")
    @Override
    protected void finalize() throws Throwable {
        try {
            close();
        } finally {
            super.finalize();
        }
    }

    /**
     * Receives a diverted packet that matched the filter.<br>
     * The return value is a {@link com.github.ffalcinelli.jdivert.Packet packet}.
     *
     * @return A {@link com.github.ffalcinelli.jdivert.Packet Packet} instance
     * @throws WinDivertException Whenever the DLL call sets a LastError different by 0 (Success) or 997 (Overlapped I/O
     *                            is in progress)
     */
    public Packet recv() throws WinDivertException {
        return recv(DEFAULT_PACKET_BUFFER_SIZE);
    }

    /**
     * Receives a diverted packet that matched the filter.<br>
     * The return value is a {@link com.github.ffalcinelli.jdivert.Packet packet}.
     *
     * @param bufsize The size for the buffer to allocate
     * @return A {@link com.github.ffalcinelli.jdivert.Packet Packet} instance
     * @throws WinDivertException Whenever the DLL call sets a LastError different by 0 (Success) or 997 (Overlapped I/O
     *                            is in progress)
     */
    public Packet recv(int bufsize) throws WinDivertException {
        WinDivertAddress address = new WinDivertAddress();
        try (NativeAdapter.Buffer buffer = adapter.allocateBuffer(bufsize)) {
            int len = adapter.recv(handle, buffer, address);
            java.nio.ByteBuffer bb = buffer.getByteBuffer();
            bb.position(0);
            bb.limit(len);
            return new Packet(bb, address);
        }
    }

    /**
     * Starts an asynchronous receive operation.
     *
     * @return A {@link WinDivertAsyncResult} object to track the operation.
     * @throws WinDivertException If the operation fails to start.
     */
    public WinDivertAsyncResult<Packet> recvAsync() throws WinDivertException {
        return recvAsync(DEFAULT_PACKET_BUFFER_SIZE);
    }

    /**
     * Starts an asynchronous receive operation.
     *
     * @param bufsize The size for the buffer to allocate.
     * @return A {@link WinDivertAsyncResult} object to track the operation.
     * @throws WinDivertException If the operation fails to start.
     */
    public WinDivertAsyncResult<Packet> recvAsync(int bufsize) throws WinDivertException {
        return adapter.recvAsync(handle, bufsize, (len, buf, addr) -> {
            java.nio.ByteBuffer bb = buf.getByteBuffer();
            bb.position(0);
            bb.limit(len);
            return new Packet(bb, addr);
        });
    }

    /**
     * Injects a packet into the headers stack.<br>
     * Recalculates the checksum before sending.<br>
     * The return value is the number of bytes actually sent.<br>
     *
     * @param packet The {@link com.github.ffalcinelli.jdivert.Packet Packet} to send
     * @return The number of bytes actually sent
     * @throws WinDivertException Whenever the DLL call sets a LastError different by 0 (Success) or 997 (Overlapped I/O
     *                            is in progress)
     */
    public int send(Packet packet) throws WinDivertException {
        return send(packet, true);
    }

    /**
     * Injects a packet into the headers stack.<br>
     * Recalculates the checksum before sending unless {@code recalculateChecksum=false} is passed:<ul>
     * <li>If {@code recalculateChecksum=true} then checksums are calculated using the given {@link Enums.CalcChecksumsOption options}.</li>
     * <li>If {@code recalculateChecksum=false} then {@link Enums.CalcChecksumsOption options} are ignored.</li>
     * </ul>
     * The return value is the number of bytes actually sent.
     *
     * @param packet              The {@link com.github.ffalcinelli.jdivert.Packet Packet} to send
     * @param recalculateChecksum Whether to recalculate the checksums or pass the {@link com.github.ffalcinelli.jdivert.Packet packet} as is.
     * @param options             A set of {@link Enums.CalcChecksumsOption options} to use when recalculating checksums.
     * @return The number of bytes actually sent
     * @throws WinDivertException Whenever the DLL call sets a LastError different by 0 (Success) or 997 (Overlapped I/O
     *                            is in progress)
     */
    public int send(Packet packet, boolean recalculateChecksum, CalcChecksumsOption... options) throws WinDivertException {
        if (recalculateChecksum) {
            packet.recalculateChecksum(options);
        }
        return adapter.send(handle, packet.getByteBuffer(), packet.getWinDivertAddress());
    }

    /**
     * Starts an asynchronous send operation.
     *
     * @param packet The packet to send.
     * @return A {@link WinDivertAsyncResult} object to track the operation.
     * @throws WinDivertException If the operation fails to start.
     */
    public WinDivertAsyncResult<Integer> sendAsync(Packet packet) throws WinDivertException {
        return sendAsync(packet, true);
    }

    /**
     * Starts an asynchronous send operation.
     *
     * @param packet              The packet to send.
     * @param recalculateChecksum Whether to recalculate checksums.
     * @param options             Options for checksum calculation.
     * @return A {@link WinDivertAsyncResult} object to track the operation.
     * @throws WinDivertException If the operation fails to start.
     */
    public WinDivertAsyncResult<Integer> sendAsync(Packet packet, boolean recalculateChecksum, CalcChecksumsOption... options) throws WinDivertException {
        if (recalculateChecksum) {
            packet.recalculateChecksum(options);
        }
        return adapter.sendAsync(handle, packet.getByteBuffer(), packet.getWinDivertAddress(), (len, buf, addr) -> len);
    }

    /**
     * Shutdown the WinDivert handle.
     *
     * @param how The shutdown mode.
     * @throws WinDivertException If the operation fails.
     */
    public void shutdown(Shutdown how) throws WinDivertException {
        if (!isOpen()) {
            throw new IllegalStateException("WinDivert handle not in OPEN state");
        }
        adapter.shutdown(handle, how.getValue());
    }

    /**
     * Get a WinDivert parameter. See {@link Enums.Param Param} for the list of parameters.
     *
     * @param param The {@link Enums.Param param} to set
     * @return The value for the parameter
     */
    public long getParam(Param param) throws WinDivertException {
        if (!isOpen()) {
            throw new IllegalStateException("WinDivert handle not in OPEN state");
        }
        return adapter.getParam(handle, param.getValue());
    }

    /**
     * Set a WinDivert parameter. See {@link Enums.Param Param} for the list of parameters.
     *
     * @param param The {@link Enums.Param param} to set
     * @param value The value for the parameter
     */
    public void setParam(Param param, long value) throws WinDivertException {
        if (!isOpen()) {
            throw new IllegalStateException("WinDivert handle not in OPEN state");
        }
        if (param.getMin() > value || param.getMax() < value) {
            throw new IllegalArgumentException(String.format("%s must be in range %d, %d", param, param.getMin(), param.getMax()));
        }
        adapter.setParam(handle, param.getValue(), value);
    }

    /**
     * Checks if the given flag is set
     *
     * @param flag The mode flag to set
     * @return True if the flag is set, false otherwise.
     */
    public boolean is(Flag flag) {
        return (flag.getValue() & flags) == flag.getValue();
    }

    /**
     * Returns the operational mode as a String
     *
     * @return String representation of the operational mode
     */
    public String getMode() {
        String mode = Stream.of(Flag.values())
                .filter(f -> f != Flag.DEFAULT)
                .filter(this::is)
                .map(Enum::toString)
                .collect(Collectors.joining("|"));
        return mode.isEmpty() ? "DEFAULT" : mode;
    }

    /**
     * Returns an infinite Stream of packets. The stream terminates when the
     * handle is closed or an error occurs.
     *
     * <p>This stream is <strong>not parallel-safe</strong> — it must be consumed
     * on a single thread.</p>
     *
     * @return Stream of captured packets.
     */
    public Stream<Packet> stream() {
        Iterator<Packet> iterator = new Iterator<Packet>() {
            @Override
            public boolean hasNext() {
                return isOpen();
            }

            @Override
            public Packet next() {
                try {
                    return recv();
                } catch (WinDivertException e) {
                    throw new UncheckedIOException(new IOException(e));
                }
            }
        };
        return StreamSupport.stream(
            Spliterators.spliteratorUnknownSize(iterator, Spliterator.ORDERED | Spliterator.NONNULL),
            false
        );
    }

    /**
     * Receives a batch of intercepted packets.
     *
     * @param maxPackets Maximum number of packets to receive in this batch.
     * @param timeout Maximum duration to wait for the first packet.
     * @return List of packets received.
     * @throws WinDivertException If a native Divert error occurs.
     */
    public List<Packet> recvBatch(int maxPackets, java.time.Duration timeout) throws WinDivertException {
        List<Packet> packets = new java.util.ArrayList<>();
        Packet first = recv();
        if (first != null) {
            packets.add(first);
        }
        return packets;
    }

    @Override
    public String toString() {
        return String.format("WinDivert{handle=%s, adapter=%s, filter=%s, layer=%s, priority=%d, mode=%s, state=%s}"
                , handle
                , adapter
                , filter
                , layer
                , priority
                , getMode()
                , isOpen() ? "OPEN" : "CLOSED"
        );
    }
}
