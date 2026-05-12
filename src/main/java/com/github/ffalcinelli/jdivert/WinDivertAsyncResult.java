/*
 * Copyright (c) Fabio Falcinelli 2024.
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

/**
 * Encapsulates the result of an asynchronous WinDivert operation.
 *
 * @param <T> The type of the result (e.g., Packet or Integer for bytes sent).
 */
public class WinDivertAsyncResult<T> implements AutoCloseable {
    private final NativeAdapter.Handle handle;
    private final NativeAdapter.Buffer buffer;
    private final WinDivertAddress address;
    private final ResultConverter<T> converter;
    private final AsyncImplementation implementation;
    private boolean completed = false;
    private boolean released = false;
    private T result;

    public WinDivertAsyncResult(NativeAdapter.Handle handle, NativeAdapter.Buffer buffer, WinDivertAddress address, ResultConverter<T> converter, AsyncImplementation implementation) {
        this.handle = handle;
        this.buffer = buffer;
        this.address = address;
        this.converter = converter;
        this.implementation = implementation;
    }

    /**
     * Checks if the asynchronous operation has completed.
     *
     * @return True if completed, false otherwise.
     */
    public boolean isCompleted() {
        if (completed) return true;
        if (implementation.isCompleted()) {
            try {
                get();
                return true;
            } catch (WinDivertException e) {
                return true;
            }
        }
        return false;
    }

    /**
     * Blocks until the asynchronous operation completes and returns the result.
     *
     * @return The result of the operation.
     * @throws WinDivertException If the operation fails.
     */
    public synchronized T get() throws WinDivertException {
        if (completed) return result;

        try {
            int len = implementation.waitAndGetResult();
            result = converter.convert(len, buffer, address);
            completed = true;
            return result;
        } finally {
            close();
        }
    }

    /**
     * Cancels the asynchronous operation and releases the associated native buffer.
     */
    public void cancel() {
        close();
    }

    /**
     * Releases the native buffer associated with this result.
     * This is automatically called by {@link #get()} after completion.
     */
    @Override
    public synchronized void close() {
        if (!released) {
            if (buffer != null) {
                buffer.close();
            }
            released = true;
        }
    }

    @FunctionalInterface
    public interface ResultConverter<T> {
        T convert(int len, NativeAdapter.Buffer buffer, WinDivertAddress address);
    }

    /**
     * Internal interface for platform-specific asynchronous implementation.
     */
    public interface AsyncImplementation {
        boolean isCompleted();

        int waitAndGetResult() throws WinDivertException;
    }
}
