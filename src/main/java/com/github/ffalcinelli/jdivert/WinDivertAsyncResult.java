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
import com.github.ffalcinelli.jdivert.windivert.WinDivertAddress;
import com.github.ffalcinelli.jdivert.windivert.WinDivertDLL;
import com.sun.jna.Memory;
import com.sun.jna.platform.win32.Kernel32;
import com.sun.jna.platform.win32.WinBase;
import com.sun.jna.platform.win32.WinNT;
import com.sun.jna.ptr.IntByReference;

import static com.github.ffalcinelli.jdivert.exceptions.WinDivertException.throwExceptionOnGetLastError;

/**
 * Encapsulates the result of an asynchronous WinDivert operation.
 *
 * @param <T> The type of the result (e.g., Packet or Integer for bytes sent).
 */
public class WinDivertAsyncResult<T> {
    private final WinNT.HANDLE handle;
    private final WinBase.OVERLAPPED overlapped;
    private final Memory buffer;
    private final WinDivertAddress address;
    private final IntByReference transferLen;
    private final ResultConverter<T> converter;
    private boolean completed = false;
    private T result;

    public interface ResultConverter<T> {
        T convert(int len, Memory buffer, WinDivertAddress address);
    }

    WinDivertAsyncResult(WinNT.HANDLE handle, WinBase.OVERLAPPED overlapped, Memory buffer, WinDivertAddress address, ResultConverter<T> converter) {
        this.handle = handle;
        this.overlapped = overlapped;
        this.buffer = buffer;
        this.address = address;
        this.transferLen = new IntByReference();
        this.converter = converter;
    }

    /**
     * Checks if the asynchronous operation has completed.
     *
     * @return True if completed, false otherwise.
     */
    public boolean isCompleted() {
        if (completed) return true;
        if (Kernel32.INSTANCE.HasOverlappedIoCompleted(overlapped)) {
            try {
                get(); // This will populate result and set completed
                return true;
            } catch (WinDivertException e) {
                return true; // Operation completed even if it failed
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

        if (!Kernel32.INSTANCE.GetOverlappedResult(handle, overlapped, transferLen, true)) {
            throwExceptionOnGetLastError();
        }
        
        if (address != null) {
            address.read();
        }

        result = converter.convert(transferLen.getValue(), buffer, address);
        completed = true;
        
        // Clean up event handle
        if (overlapped.hEvent != null && overlapped.hEvent != WinNT.INVALID_HANDLE_VALUE) {
            Kernel32.INSTANCE.CloseHandle(overlapped.hEvent);
            overlapped.hEvent = null;
        }

        return result;
    }
}
