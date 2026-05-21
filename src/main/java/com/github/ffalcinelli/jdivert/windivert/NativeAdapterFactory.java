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
 * This version dynamically loads the correct adapter at runtime.
 */
public class NativeAdapterFactory {
    private static final NativeAdapter INSTANCE;

    static {
        NativeAdapter adapter = null;
        try {
            String javaVersion = System.getProperty("java.version");
            int majorVersion = getJavaMajorVersion(javaVersion);
            if (majorVersion >= 22) {
                try {
                    Class<?> clazz = Class.forName("com.github.ffalcinelli.jdivert.windivert.PanamaNativeAdapter");
                    adapter = (NativeAdapter) clazz.getDeclaredConstructor().newInstance();
                } catch (Throwable t) {
                    // Panama adapter not available or failed to load, fallback to JNA
                }
            }
        } catch (Throwable t) {
            // Fallback to JNA
        }
        if (adapter == null) {
            adapter = new JnaNativeAdapter();
        }
        INSTANCE = adapter;
    }

    public static NativeAdapter getAdapter() {
        return INSTANCE;
    }

    private static int getJavaMajorVersion(String version) {
        String[] parts = version.split("\\.");
        if (parts[0].equals("1")) {
            return Integer.parseInt(parts[1]);
        } else {
            return Integer.parseInt(parts[0]);
        }
    }
}
