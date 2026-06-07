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

import com.github.ffalcinelli.jdivert.Util;

/**
 * Factory for creating NativeAdapter instances.
 * This version dynamically loads the correct adapter at runtime based on OS and JVM version.
 */
public class NativeAdapterFactory {
    private static final NativeAdapter INSTANCE;

    static {
        NativeAdapter adapter = null;
        if (Util.isWindows()) {
            adapter = loadAdapter("com.github.ffalcinelli.jdivert.windivert.WinDivertPanamaNativeAdapter",
                    "com.github.ffalcinelli.jdivert.windivert.WinDivertJnaNativeAdapter");
        } else if (Util.isLinux()) {
            adapter = loadAdapter("com.github.ffalcinelli.jdivert.ebpfdivert.EBPFDivertPanamaNativeAdapter",
                    "com.github.ffalcinelli.jdivert.ebpfdivert.EBPFDivertJnaNativeAdapter");
        }

        if (adapter == null) {
            throw new RuntimeException("Unsupported platform or unable to load native adapter. OS: " + System.getProperty("os.name"));
        }
        INSTANCE = adapter;
    }

    private static NativeAdapter loadAdapter(String panamaClassName, String jnaClassName) {
        NativeAdapter adapter = null;
        ClassLoader cl = NativeAdapterFactory.class.getClassLoader();
        try {
            int majorVersion = getJavaMajorVersion(System.getProperty("java.version"));
            if (majorVersion >= 22) {
                try {
                    Class<?> clazz = Class.forName(panamaClassName, true, cl);
                    adapter = (NativeAdapter) clazz.getDeclaredConstructor().newInstance();
                } catch (Throwable t) {
                    // Panama adapter not available or failed to load, fallback to JNA
                }
            }
        } catch (Throwable t) {
            // Fallback to JNA
        }
        if (adapter == null) {
            try {
                Class<?> clazz = Class.forName(jnaClassName, true, cl);
                adapter = (NativeAdapter) clazz.getDeclaredConstructor().newInstance();
            } catch (Throwable t) {
                // Unable to load JNA adapter
            }
        }
        return adapter;
    }

    public static NativeAdapter getAdapter() {
        return INSTANCE;
    }

    private static int getJavaMajorVersion(String version) {
        String[] parts = version.split("\\.");
        if (parts[0].equals("1")) {
            return Integer.parseInt(parts[1]);
        } else {
            // Remove any suffix like -ea, -internal, etc.
            String major = parts[0];
            int dashIndex = major.indexOf('-');
            if (dashIndex != -1) {
                major = major.substring(0, dashIndex);
            }
            return Integer.parseInt(major);
        }
    }
}
