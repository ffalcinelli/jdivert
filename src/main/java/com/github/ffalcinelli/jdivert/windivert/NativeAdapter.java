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

import com.github.ffalcinelli.jdivert.WinDivertAsyncResult;
import com.github.ffalcinelli.jdivert.exceptions.WinDivertException;

/**
 * Internal interface for native WinDivert operations.
 * Abstracted to support both JNA and Project Panama (FFM API).
 */
public interface NativeAdapter {
    /**
     * Handle type for native handles.
     */
    interface Handle extends AutoCloseable {
        @Override
        void close() throws WinDivertException;

        boolean isValid();
    }

    /**
     * Native buffer for packet data.
     */
    interface Buffer extends AutoCloseable {
        java.nio.ByteBuffer getByteBuffer();

        int capacity();

        @Override
        void close();
    }

    Handle open(String filter, int layer, short priority, long flags) throws WinDivertException;

    int recv(Handle handle, Buffer buffer, WinDivertAddress address) throws WinDivertException;

    <T> WinDivertAsyncResult<T> recvAsync(Handle handle, int bufsize, WinDivertAsyncResult.ResultConverter<T> converter) throws WinDivertException;

    int send(Handle handle, java.nio.ByteBuffer packet, WinDivertAddress address) throws WinDivertException;

    <T> WinDivertAsyncResult<T> sendAsync(Handle handle, java.nio.ByteBuffer packet, WinDivertAddress address, WinDivertAsyncResult.ResultConverter<T> converter) throws WinDivertException;

    void shutdown(Handle handle, int how) throws WinDivertException;

    void setParam(Handle handle, int param, long value) throws WinDivertException;

    long getParam(Handle handle, int param) throws WinDivertException;

    int calcChecksums(byte[] packet, WinDivertAddress address, long flags);

    long hashPacket(byte[] packet, long seed);

    Buffer allocateBuffer(int size);

    String formatMessage(int errorCode);

    int getLastError();
}
