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

/**
 * A platform-neutral handle used to capture, modify, and inject network packets.
 * <p>
 * This class serves as the primary entry point for JDivert, supporting both Windows (via WinDivert)
 * and Linux (via eBPF). It extends {@link WinDivert} for backward compatibility.
 * </p>
 */
public class Divert extends WinDivert {

    /**
     * Create a new Divert instance based upon the given filter for
     * {@link Enums.Layer#NETWORK NETWORK} layer with priority set to 0 and in
     * {@link Enums.Flag#DEFAULT DEFAULT} mode.
     *
     * @param filter The filter string.
     */
    public Divert(String filter) {
        super(filter);
    }

    /**
     * Create a new Divert instance based upon the given parameters.
     *
     * @param filter   The filter string.
     * @param layer    The {@link Enums.Layer layer}.
     * @param priority The priority of the handle.
     * @param flags    Additional {@link Enums.Flag flags}.
     */
    public Divert(String filter, Enums.Layer layer, int priority, Enums.Flag... flags) {
        super(filter, layer, priority, flags);
    }

    /**
     * Opens the handle for the given filter.
     *
     * @return this instance to allow call chaining.
     * @throws WinDivertException If the native call fails.
     */
    @Override
    public Divert open() throws WinDivertException {
        super.open();
        return this;
    }
}
