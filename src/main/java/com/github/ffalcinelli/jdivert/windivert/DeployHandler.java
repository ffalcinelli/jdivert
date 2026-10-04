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
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFileAttributes;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.nio.file.attribute.UserPrincipal;
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
     * @return the jar resource of the native library for the current OS and architecture,
     * as {resource path, deployed file name} pairs
     * @throws IOException if the platform is not supported
     */
    static String[][] nativeResources() throws IOException {
        if (Util.isWindows()) {
            return new String[][]{{"WinDivert64.dll", "WinDivert64.dll"}, {"WinDivert64.sys", "WinDivert64.sys"}};
        } else if (Util.isLinux()) {
            return new String[][]{{linuxPlatform() + "/libebpfdivert.so", "libebpfdivert.so"}};
        }
        throw new IOException("Unsupported operating system: " + System.getProperty("os.name"));
    }

    /**
     * @return the JNA-style platform prefix of the bundled Linux library
     * @throws IOException if the architecture is not supported
     */
    static String linuxPlatform() throws IOException {
        String arch = System.getProperty("os.arch", "").toLowerCase(java.util.Locale.ROOT);
        if (arch.equals("amd64") || arch.equals("x86_64")) {
            return "linux-x86-64";
        }
        if (arch.equals("aarch64") || arch.equals("arm64")) {
            return "linux-aarch64";
        }
        throw new IOException("Unsupported Linux architecture: " + arch);
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

        for (String[] entry : nativeResources()) {
            String file = entry[0];
            File copyFile = new File(deployDir, entry[1]);

            java.net.URL resource = DeployHandler.class.getClassLoader().getResource(file);
            if (resource == null) {
                // Try system class loader as fallback
                resource = ClassLoader.getSystemClassLoader().getResource(file);
            }
            if (resource == null) {
                throw new IOException("Resource " + file + " not found");
            }

            byte[] content;
            try (InputStream source = resource.openStream();
                 java.io.ByteArrayOutputStream sink = new java.io.ByteArrayOutputStream()) {
                copy(source, sink);
                content = sink.toByteArray();
            }
            if (copyFile.exists() && Arrays.equals(Files.readAllBytes(copyFile.toPath()), content)) {
                continue; // Already deployed
            }
            // Write aside and rename, so a concurrent process never loads a partial file.
            Path tmp = Files.createTempFile(deployDir.toPath(), entry[1], ".tmp");
            try {
                Files.write(tmp, content);
                Files.move(tmp, copyFile.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } finally {
                Files.deleteIfExists(tmp);
            }
        }
        return deployDir.getAbsolutePath();
    }

    /**
     * Returns a deploy directory only the current user can write to.  Native libraries are
     * loaded from it by privileged processes, so a directory another user could have created
     * (e.g. in a shared /tmp) must not be trusted.
     *
     * @return the directory to deploy into
     * @throws IOException if no private directory can be created
     */
    static File privateDeployDir() throws IOException {
        String tmpDir = System.getProperty("java.io.tmpdir");
        String user = System.getProperty("user.name", "user").replaceAll("[^A-Za-z0-9._-]", "_");
        File dir = new File(tmpDir, "jdivert-" + VERSION + "-" + user);
        if (Util.isWindows()) {
            return dir;
        }
        Path path = dir.toPath();
        try {
            if (!Files.exists(path, LinkOption.NOFOLLOW_LINKS)) {
                Files.createDirectory(path, PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------")));
            }
            PosixFileAttributes attrs = Files.readAttributes(path, PosixFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
            UserPrincipal me = path.getFileSystem().getUserPrincipalLookupService()
                    .lookupPrincipalByName(System.getProperty("user.name"));
            boolean groupOrOtherWrite = attrs.permissions().contains(PosixFilePermission.GROUP_WRITE)
                    || attrs.permissions().contains(PosixFilePermission.OTHERS_WRITE);
            if (attrs.isDirectory() && !attrs.isSymbolicLink() && attrs.owner().equals(me) && !groupOrOtherWrite) {
                return dir;
            }
        } catch (IOException | UnsupportedOperationException ignored) {
            // Fall through to a fresh private directory.
        }
        File fresh = Files.createTempDirectory("jdivert-" + VERSION + "-").toFile();
        fresh.deleteOnExit();
        return fresh;
    }

    /**
     * Deploys binaries and returns the path to the primary library (WinDivert64.dll on Windows,
     * libebpfdivert.so on Linux).
     *
     * @return The path to the deployed binary.
     */
    public static Path deployToPath() {
        if (!is64Bit()) {
            throw new RuntimeException("JDivert supports 64-bit architecture only.");
        }
        try {
            String deployedPath = deployInTempDir(privateDeployDir());
            String mainFile = Util.isWindows() ? "WinDivert64.dll" : "libebpfdivert.so";
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
