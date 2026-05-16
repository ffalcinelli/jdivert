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
import static org.junit.jupiter.api.Assertions.*;

public class NativeAdapterFactoryTestCase {

    @Test
    public void testCorrectAdapterUsed() {
        NativeAdapter adapter = NativeAdapterFactory.getAdapter();
        assertNotNull(adapter);
        
        String javaVersion = System.getProperty("java.version");
        int majorVersion = getJavaMajorVersion(javaVersion);
        
        if (majorVersion >= 22) {
            assertEquals("com.github.ffalcinelli.jdivert.windivert.PanamaNativeAdapter", adapter.getClass().getName(),
                "On Java 22+, PanamaNativeAdapter should be used. Detected Java version: " + javaVersion);
        } else {
            assertEquals("com.github.ffalcinelli.jdivert.windivert.JnaNativeAdapter", adapter.getClass().getName(),
                "On Java < 22, JnaNativeAdapter should be used. Detected Java version: " + javaVersion);
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
}
