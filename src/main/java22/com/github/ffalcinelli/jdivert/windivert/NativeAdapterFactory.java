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

/**
 * Factory for creating NativeAdapter instances.
 * This version is for Java 22+ and uses Project Panama (FFM API).
 */
public class NativeAdapterFactory {
    private static final NativeAdapter INSTANCE = new PanamaNativeAdapter();

    public static NativeAdapter getAdapter() {
        return INSTANCE;
    }
}
