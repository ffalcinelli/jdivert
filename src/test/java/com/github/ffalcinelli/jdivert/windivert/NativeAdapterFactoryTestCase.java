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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;

public class NativeAdapterFactoryTestCase {

    @Test
    @EnabledOnOs(OS.WINDOWS)
    public void testCorrectAdapterUsed() {
        NativeAdapter adapter = NativeAdapterFactory.getAdapter();
        assertNotNull(adapter);
        
        String javaVersion = System.getProperty("java.version");
        int majorVersion = getJavaMajorVersion(javaVersion);
        
        if (majorVersion >= 22) {
            assertEquals("com.github.ffalcinelli.jdivert.windivert.WinDivertPanamaNativeAdapter", adapter.getClass().getName(),
                "On Java 22+, WinDivertPanamaNativeAdapter should be used. Detected Java version: " + javaVersion);
        } else {
            assertEquals("com.github.ffalcinelli.jdivert.windivert.WinDivertJnaNativeAdapter", adapter.getClass().getName(),
                "On Java < 22, WinDivertJnaNativeAdapter should be used. Detected Java version: " + javaVersion);
        }
    }

    private int getJavaMajorVersion(String version) {
        String[] parts = version.split("\\.");
        if (parts[0].equals("1")) {
            return Integer.parseInt(parts[1]);
        } else {
            return Integer.parseInt(parts[0]);
        }
    }

    /** Its constructor fails, like an adapter whose native library cannot be loaded. */
    public static class FailingAdapter {
        public FailingAdapter() {
            throw new IllegalStateException("no native library");
        }
    }

    private static Method factoryMethod(String name, Class<?>... types) throws NoSuchMethodException {
        Method m = NativeAdapterFactory.class.getDeclaredMethod(name, types);
        m.setAccessible(true);
        return m;
    }

    @Test
    public void javaMajorVersionParsing() throws Exception {
        Method m = factoryMethod("getJavaMajorVersion", String.class);
        assertEquals(8, m.invoke(null, "1.8.0_292"));
        assertEquals(22, m.invoke(null, "22-ea"));
        assertEquals(25, m.invoke(null, "25.0.1"));
    }

    @Test
    public void loadAdapterReportsEveryFailure() throws Exception {
        Method m = factoryMethod("loadAdapter", String.class, String.class, StringBuilder.class);
        String failing = FailingAdapter.class.getName();
        StringBuilder report = new StringBuilder();
        assertNull(m.invoke(null, failing, failing, report));
        assertTrue(report.toString().contains(failing + " failed"), report.toString());
        assertTrue(report.toString().contains("no native library"), report.toString());

        String version = System.getProperty("java.version");
        try {
            System.setProperty("java.version", "unknown");
            report.setLength(0);
            assertNull(m.invoke(null, failing, "no.such.Adapter", report));
            assertTrue(report.toString().contains("Java version check failed"), report.toString());
            assertTrue(report.toString().contains("no.such.Adapter failed"), report.toString());
        } finally {
            System.setProperty("java.version", version);
        }
    }
}
