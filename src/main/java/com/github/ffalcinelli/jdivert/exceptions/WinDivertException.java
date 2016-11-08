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

package com.github.ffalcinelli.jdivert.exceptions;

import com.sun.jna.LastErrorException;
import com.sun.jna.Native;
import com.sun.jna.platform.win32.Kernel32Util;

/**
 * Created by fabio on 21/10/2016.
 */
public class WinDivertException extends Exception {
    protected int code;
    protected String message;
    protected LastErrorException lee;

    public WinDivertException(int code) {
        this(code, Kernel32Util.formatMessage(code));
    }

    public WinDivertException(int code, String message) {
        this(code, message, null);
    }

    public WinDivertException(int code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    /**
     * Throw a WinDivertException whenever GetLastError returned a code different from
     * - 0 (Success)
     * - 997 (Overlapped I/O is in progress)
     *
     * @return The GetLastError code
     * @throws WinDivertException When {@code GetLastError} returns a code different from 0 or 997, {@link WinDivertException} is thrown.
     */
    public static int throwExceptionOnGetLastError() throws WinDivertException {
        int lastError = Native.getLastError();
        if (lastError != 0 && lastError != 997) {
            throw new WinDivertException(lastError);
        }
        return lastError;
    }

    public int getCode() {
        return code;
    }

    @Override
    public String toString() {
        return "WinDivertException{" +
                "code=" + code +
                ", message='" + message + '\'' +
                '}';
    }
}
