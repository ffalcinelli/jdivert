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

package com.github.ffalcinelli.jdivert.network;

import java.nio.ByteBuffer;

/**
 * Created by fabio on 24/10/2016.
 */
public abstract class TransportHeader extends Header {

    public TransportHeader(ByteBuffer raw, int offset) {
        super(raw, offset);
    }

    public int getSrcPort() {
        return unsigned(raw.getShort(start));
    }

    public void setSrcPort(int port) {
        raw.putShort(start, (short) port);
    }

    public int getDstPort() {
        return unsigned(raw.getShort(start + 2));
    }

    public void setDstPort(int port) {
        raw.putShort(start + 2, (short) port);
    }

}
