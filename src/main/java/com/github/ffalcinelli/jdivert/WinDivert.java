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
import com.github.ffalcinelli.jdivert.windivert.WinDivertAddress;
import com.github.ffalcinelli.jdivert.windivert.WinDivertDLL;
import com.sun.jna.Memory;
import com.sun.jna.ptr.IntByReference;
import com.sun.jna.ptr.LongByReference;

import java.util.Arrays;
import java.util.List;

import static com.github.ffalcinelli.jdivert.Consts.*;
import static com.github.ffalcinelli.jdivert.exceptions.WinDivertException.throwExceptionOnGetLastError;
import static com.sun.jna.platform.win32.WinNT.HANDLE;

/**
 * Created by fabio on 20/10/2016.
 */
public class WinDivert {
    public static int DEFAULT_PACKET_BUFFER_SIZE = 1500;
    private WinDivertDLL dll = WinDivertDLL.INSTANCE;
    private String filter;
    private Layer layer;
    private int priority;
    private int flags;
    private HANDLE handle;

    /**
     * Create a new WinDivert instance based upon the given filter
     * @param filter
     */
    public WinDivert(String filter) {
        this(filter, Layer.NETWORK, 0, Flag.SNIFF);
    }


    public WinDivert(String filter, Layer layer, int priority, Flag... flags) {
        this.filter = filter;
        this.layer = layer;
        this.priority = priority;
        this.flags = 0;
        List<Flag> flagList = Arrays.asList(flags);
        if (flagList.contains(Flag.SNIFF) && flagList.contains(Flag.DROP)) {
            throw new IllegalArgumentException(String.format("A filter cannot be set with flags %s and %s at same time.", Flag.SNIFF, Flag.DROP));
        }
        for (Flag flag : flags) {
            this.flags |= flag.getValue();
        }

    }

    public WinDivert open() throws WinDivertException {
        if (isOpen()) {
            throw new IllegalStateException("The instance is already in open state");
        }
        handle = dll.WinDivertOpen(filter, layer.getValue(), (short) priority, flags);
        throwExceptionOnGetLastError();
        //Allow call chaining
        return this;
    }

    public boolean isOpen() {
        return handle != null;
    }

    public void close() throws WinDivertException {
        if (isOpen()) {
            try {
                dll.WinDivertClose(handle);
                throwExceptionOnGetLastError();
            } finally {
                handle = null;
            }
        }
    }

    public Packet recv() throws WinDivertException {
        return recv(DEFAULT_PACKET_BUFFER_SIZE);
    }

    public Packet recv(int bufsize) throws WinDivertException {
        WinDivertAddress address = new WinDivertAddress();
        Memory buffer = new Memory(bufsize);
        IntByReference recvLen = new IntByReference();
        dll.WinDivertRecv(handle, buffer, bufsize, address.getPointer(), recvLen);
        throwExceptionOnGetLastError();
        byte[] raw = buffer.getByteArray(0, recvLen.getValue());
        return new Packet(raw, address);
    }

    public int send(Packet packet) throws WinDivertException {
        return send(packet, false);
    }

    public int send(Packet packet, CalcChecksumsOption... options) throws WinDivertException {
        return send(packet, true, options);
    }

    public int send(Packet packet, boolean recalculateChecksum, CalcChecksumsOption... options) throws WinDivertException {
        if (recalculateChecksum) {
            packet.recalculateChecksum(options);
        }
        IntByReference sendLen = new IntByReference();
        byte[] raw = packet.getRaw();
        Memory buffer = new Memory(raw.length);

        buffer.write(0, raw, 0, raw.length);
        dll.WinDivertSend(handle, buffer, raw.length, packet.getWinDivertAddress().getPointer(), sendLen);
        throwExceptionOnGetLastError();
        return sendLen.getValue();
    }

    public long getParam(Param param) {
        if (!isOpen()) {
            throw new IllegalStateException("WinDivert handle not in OPEN state");
        }
        LongByReference value = new LongByReference();
        dll.WinDivertGetParam(handle, param.getValue(), value);
        return value.getValue();
    }

    public void setParam(Param param, long value) {
        if (!isOpen()) {
            throw new IllegalStateException("WinDivert handle not in OPEN state");
        }
        if (param.getMin() > value || param.getMax() < value) {
            throw new IllegalArgumentException(String.format("%s must be in range %d, %d", param, param.getMin(), param.getMax()));
        }
        dll.WinDivertSetParam(handle, param.getValue(), value);
    }


}
