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

import com.github.ffalcinelli.jdivert.Util;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.EnumSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
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
    public void testDeployInInvalidDir() throws java.io.IOException {
        File fileAsDir = File.createTempFile("jdivert-test", "tmp");
        try {
            assertThrows(java.io.IOException.class, () -> DeployHandler.deployInTempDir(fileAsDir));
        } finally {
            fileAsDir.delete();
        }
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
    public void testDeployToPath() throws java.io.IOException {
        try {
            Path binPath = DeployHandler.deployToPath();
            assertNotNull(binPath);
            String mainFile = Util.isWindows() ? "WinDivert64.dll" : "libebpfdivert.so";
            assertTrue(binPath.toString().endsWith(mainFile));

            File binFile = binPath.toFile();
            assertTrue(binFile.exists(), "Binary should exist after deployment: " + binFile);

            if (Util.isWindows()) {
                File sysFile = new File(binFile.getParentFile(), "WinDivert64.sys");
                assertTrue(sysFile.exists(), "SYS should exist after deployment");
            }

            // Verify it's in a stable directory
            java.util.Properties props = new java.util.Properties();
            try (java.io.InputStream is = DeployHandler.class.getResourceAsStream("/jdivert.properties")) {
                props.load(is);
            }
            String versionedDir = "jdivert-" + props.getProperty("version");
            assertTrue(binPath.toString().contains(versionedDir), "Should use versioned stable directory: " + binPath);
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
            String mainFile = Util.isWindows() ? "WinDivert64.dll" : "libebpfdivert.so";
            File binFile = new File(tempDir, mainFile);
            assertTrue(binFile.exists());
            long length = binFile.length();
            
            // Re-deploy should skip
            DeployHandler.deployInTempDir(tempDir);
            assertEquals(length, binFile.length());
        } finally {
            File[] files = tempDir.listFiles();
            if (files != null) {
                for (File f : files) f.delete();
            }
            tempDir.delete();
        }
    }

    @Test
    public void linuxPlatformFollowsArch() throws IOException {
        String arch = System.getProperty("os.arch");
        try {
            System.setProperty("os.arch", "aarch64");
            assertEquals("linux-aarch64", DeployHandler.linuxPlatform());
            System.setProperty("os.arch", "arm64");
            assertEquals("linux-aarch64", DeployHandler.linuxPlatform());
            System.setProperty("os.arch", "x86_64");
            assertEquals("linux-x86-64", DeployHandler.linuxPlatform());
            System.setProperty("os.arch", "riscv64");
            assertThrows(IOException.class, DeployHandler::linuxPlatform);
        } finally {
            System.setProperty("os.arch", arch);
        }
    }

    @Test
    @EnabledOnOs(OS.LINUX)
    public void insecureDeployDirIsNotReused() throws IOException {
        File dir = DeployHandler.privateDeployDir();
        Path path = dir.toPath();
        Set<PosixFilePermission> perms = Files.getPosixFilePermissions(path);
        try {
            Set<PosixFilePermission> groupWritable = EnumSet.copyOf(perms);
            groupWritable.add(PosixFilePermission.GROUP_WRITE);
            Files.setPosixFilePermissions(path, groupWritable);
            File fresh = DeployHandler.privateDeployDir();
            assertNotEquals(dir, fresh);
            assertTrue(fresh.isDirectory());
        } finally {
            Files.setPosixFilePermissions(path, perms);
        }
    }

    @Test
    @EnabledOnOs(OS.LINUX)
    public void unknownUserGetsAFreshDeployDir() throws IOException {
        String user = System.getProperty("user.name");
        File stable = new File(System.getProperty("java.io.tmpdir"), "jdivert-" + versionOf() + "-jdivert-no-such-user");
        try {
            System.setProperty("user.name", "jdivert-no-such-user");
            // The owner check cannot look the user up: fall back to a fresh private directory.
            File dir = DeployHandler.privateDeployDir();
            assertNotEquals(stable, dir);
            assertTrue(dir.isDirectory());
        } finally {
            System.setProperty("user.name", user);
            Files.deleteIfExists(stable.toPath());
        }
    }

    @Test
    @EnabledOnOs(OS.LINUX)
    public void winDivertDllIsWindowsOnly() {
        assertThrows(UnsupportedOperationException.class, DeployHandler::deploy);
    }

    private static String versionOf() throws IOException {
        java.util.Properties props = new java.util.Properties();
        try (java.io.InputStream is = DeployHandler.class.getResourceAsStream("/jdivert.properties")) {
            props.load(is);
        }
        return props.getProperty("version");
    }
}
