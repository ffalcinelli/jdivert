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

import com.sun.jna.Library;
import com.sun.jna.Pointer;
import com.sun.jna.ptr.IntByReference;
import com.sun.jna.ptr.LongByReference;

import static com.sun.jna.platform.win32.WinDef.BOOL;
import static com.sun.jna.platform.win32.WinNT.HANDLE;

/**
 * Created by fabio on 20/10/2016.
 * <p>
 * DLL methods cannot declare "throws LastErrorException since 997 (Overlapped I/O is in progress) will be considered as
 * an error and will interrupt the call.
 * </p>
 */
public interface WinDivertDLL extends Library {
    WinDivertDLL INSTANCE = DeployHandler.deploy();

    HANDLE WinDivertOpen(
            String filter,
            int layer,
            short priority,
            long flags
    );

    BOOL WinDivertRecv(
            HANDLE handle,
            Pointer pPacket,
            int packetLen,
            Pointer pAddr,
            IntByReference recvLen
    );

    BOOL WinDivertSend(
            HANDLE handle,
            Pointer pPacket,
            int packetLen,
            Pointer pAddr,
            IntByReference sendLen
    );

    BOOL WinDivertSetParam(
            HANDLE handle,
            int param,
            long value);

    BOOL WinDivertGetParam(
            HANDLE handle,
            int param,
            LongByReference pValue);

    BOOL WinDivertClose(
            HANDLE handle
    );

    int WinDivertHelperCalcChecksums(
            Pointer pPacket,
            int packetLen,
            long flags
    );
}
