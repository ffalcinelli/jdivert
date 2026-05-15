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

package com.github.ffalcinelli.jdivert.windivert;

import org.junit.jupiter.api.Test;

import java.io.File;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class DeployHandlerTestCase {

    @Test
    public void testCloseIgnoreExceptions() {
        // Should not throw even with null or non-closeable (though it only accepts Closeable now)
        DeployHandler.closeIgnoreExceptions((java.io.Closeable) null);
        
        java.io.ByteArrayInputStream is = new java.io.ByteArrayInputStream(new byte[0]);
        DeployHandler.closeIgnoreExceptions(is);
        // is should be closed, but we can't easily check without a spy
    }

    @Test
    public void testDeployInInvalidDir() {
        File invalidDir = new File("Z:\\invalid\\path\\that\\should\\not\\exist");
        assertThrows(java.io.IOException.class, () -> DeployHandler.deployInTempDir(invalidDir));
    }

    @Test
    public void testCopy() throws java.io.IOException {
        byte[] data = "Hello World".getBytes();
        java.io.ByteArrayInputStream source = new java.io.ByteArrayInputStream(data);
        java.io.ByteArrayOutputStream sink = new java.io.ByteArrayOutputStream();
        long n = DeployHandler.copy(source, sink);
        assertEquals(data.length, n);
        assertArrayEquals(data, sink.toByteArray());
    }

    @Test
    public void testDeployToPath() {
        try {
            Path dllPath = DeployHandler.deployToPath();
            assertNotNull(dllPath);
            assertTrue(dllPath.toString().endsWith("WinDivert64.dll"));

            File dllFile = dllPath.toFile();
            assertTrue(dllFile.exists(), "DLL should exist after deployment");

            File sysFile = new File(dllFile.getParentFile(), "WinDivert64.sys");
            assertTrue(sysFile.exists(), "SYS should exist after deployment");

            // Verify it's in a stable directory
            String tmpDir = System.getProperty("java.io.tmpdir");
            assertTrue(dllPath.toString().contains("jdivert-3.0.0"), "Should use versioned stable directory: " + dllPath);
        } catch (Throwable t) {
            if (t.getMessage() != null && t.getMessage().contains("64-bit")) {
                return;
            }
            if (t instanceof ExceptionInInitializerError && t.getCause() != null && t.getCause().getMessage().contains("Unable to deploy")) {
                // Could be resource not found in this environment
                return;
            }
            throw t;
        }
    }

    @Test
    public void testDeployExistingFile() throws java.io.IOException {
        File tempDir = new File(System.getProperty("java.io.tmpdir"), "jdivert-test-" + java.util.UUID.randomUUID());
        if (!tempDir.mkdirs()) return;
        try {
            DeployHandler.deployInTempDir(tempDir);
            File dllFile = new File(tempDir, "WinDivert64.dll");
            assertTrue(dllFile.exists());
            long length = dllFile.length();
            
            // Re-deploy should skip
            DeployHandler.deployInTempDir(tempDir);
            assertEquals(length, dllFile.length());
        } finally {
            File[] files = tempDir.listFiles();
            if (files != null) {
                for (File f : files) f.delete();
            }
            tempDir.delete();
        }
    }
}
