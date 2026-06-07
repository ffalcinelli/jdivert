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

import java.io.Closeable;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Objects;
import java.util.Properties;

/**
 * Handles WinDivert and ebpfdivert binaries deployment to a temporary directory.
 * <p>
 * This project supports 64-bit architecture only.
 */
public class DeployHandler {

    public static int BUFFER_SIZE = 512;
    private static final String VERSION;

    static {
        String version = "unknown";
        try (InputStream is = DeployHandler.class.getResourceAsStream("/jdivert.properties")) {
            if (is != null) {
                Properties props = new Properties();
                props.load(is);
                version = props.getProperty("version", "unknown");
            }
        } catch (IOException ignored) {
        }
        VERSION = version;
    }


    /**
     * Copies all bytes from source to sink streams.
     *
     * @param source The source stream to copy from.
     * @param sink   The sink stream to copy to.
     * @return How many bytes have been copied.
     * @throws IOException Whenever an error occurs in the copy process.
     */
    public static long copy(InputStream source, OutputStream sink)
            throws IOException {
        long nread = 0L;
        byte[] buf = new byte[BUFFER_SIZE];
        int n;
        while ((n = source.read(buf)) > 0) {
            sink.write(buf, 0, n);
            nread += n;
        }
        return nread;
    }


    /**
     * Closes each stream, and more generally each {@link java.io.Closeable} ignoring any {@link java.io.IOException} may occur.
     *
     * @param closeables The {@link java.io.Closeable} objects to close.
     */
    public static void closeIgnoreExceptions(Closeable... closeables) {
        Arrays.stream(closeables)
                .filter(Objects::nonNull)
                .forEach(c -> {
                    try {
                        c.close();
                    } catch (IOException ignore) {
                    }
                });
    }

    /**
     * Deploys the necessary binaries in a temporary directory based on the current OS.
     *
     * @param deployDir The directory where to deploy the binaries.
     * @return The temporary directory absolute path.
     * @throws IOException Whenever the deploy process encounters an error.
     */
    public static String deployInTempDir(File deployDir) throws IOException {
        if (!deployDir.exists() && !deployDir.mkdirs()) {
            throw new IOException("Could not create deploy directory " + deployDir.getAbsolutePath());
        }

        String[] files;
        if (Util.isWindows()) {
            files = new String[]{"WinDivert64.dll", "WinDivert64.sys"};
        } else if (Util.isLinux()) {
            files = new String[]{"ebpfdivert.bpf.o"};
        } else {
            throw new IOException("Unsupported operating system: " + System.getProperty("os.name"));
        }

        for (String file : files) {
            File copyFile = new File(deployDir, file);

            java.net.URL resource = DeployHandler.class.getClassLoader().getResource(file);
            if (resource == null) {
                // Try system class loader as fallback
                resource = ClassLoader.getSystemClassLoader().getResource(file);
            }
            if (resource == null) {
                throw new IOException("Resource " + file + " not found");
            }

            long resourceSize = -1;
            try {
                resourceSize = resource.openConnection().getContentLengthLong();
            } catch (Exception ignore) {
            }

            if (copyFile.exists() && (resourceSize == -1 || copyFile.length() == resourceSize)) {
                continue; // Skip extraction if file exists and has same size
            }

            try (InputStream source = resource.openStream();
                 OutputStream sink = new FileOutputStream(copyFile)) {
                copy(source, sink);
            }
        }
        return deployDir.getAbsolutePath();
    }

    /**
     * Deploys binaries and returns the path to the primary library (DLL on Windows, .o on Linux).
     *
     * @return The path to the deployed binary.
     */
    public static Path deployToPath() {
        if (!is64Bit()) {
            throw new RuntimeException("JDivert supports 64-bit architecture only.");
        }
        try {
            String tmpDir = System.getProperty("java.io.tmpdir");
            File deployDir = new File(tmpDir, "jdivert-" + VERSION);

            String deployedPath = deployInTempDir(deployDir);
            String mainFile = Util.isWindows() ? "WinDivert64.dll" : "ebpfdivert.bpf.o";
            return Paths.get(deployedPath, mainFile);
        } catch (Exception e) {
            throw new ExceptionInInitializerError(new Exception("Unable to deploy binaries", e));
        }
    }

    /**
     * Compatibility method for JNA (WinDivert only).
     *
     * @return The WinDivertDLL instance.
     */
    public static WinDivertDLL deploy() {
        if (!Util.isWindows()) {
            throw new UnsupportedOperationException("WinDivertDLL is only available on Windows.");
        }
        Path dllPath = deployToPath();
        String deployedPath = dllPath.getParent().toString();
        String jnaLibraryPath = System.getProperty("jna.library.path");
        try {
            System.setProperty("jna.library.path", deployedPath);
            return com.sun.jna.Native.load("WinDivert64", WinDivertDLL.class);
        } finally {
            if (jnaLibraryPath != null)
                System.setProperty("jna.library.path", jnaLibraryPath);
        }
    }

    private static boolean is64Bit() {
        String arch = System.getProperty("os.arch");
        return arch.contains("64");
    }

}
