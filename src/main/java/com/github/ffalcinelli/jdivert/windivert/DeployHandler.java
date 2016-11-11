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

import com.sun.jna.Native;
import com.sun.jna.Platform;

import java.io.*;

/**
 * Handles WinDivert DLL and SYS files deployment to a temporary directory.
 * Without this step, WinDivert would not be able to locate SYS file for its Windows Service.
 * <p>
 * Created by fabio on 10/11/2016.
 */
public class DeployHandler {

    public static int BUFFER_SIZE = 512;

    /**
     * Creates a Temporary directory
     *
     * @return The {@link java.io.File} object for temporary directory
     * @throws IOException If the directory cannot be set as a proper temporary directory
     */
    public static File createTempDirectory()
            throws IOException {
        final File temp = File.createTempFile("temp", Long.toString(System.nanoTime()));
        if (!(temp.delete()) || !(temp.mkdir())) {
            throw new IOException("Could not create a proper temp dir at " + temp.getAbsolutePath());
        }
        return (temp);
    }

    /**
     * Copies all bytes from source to sink streams
     *
     * @param source The source stream to copy from
     * @param sink   The sink stream to copy to
     * @return How many bytes have been copied
     * @throws IOException Whenever an error occurs in the copy process
     */
    public static long copy(InputStream source, OutputStream sink)
            throws IOException {
        try {
            long nread = 0L;
            byte[] buf = new byte[BUFFER_SIZE];
            int n;
            while ((n = source.read(buf)) > 0) {
                sink.write(buf, 0, n);
                nread += n;
            }
            return nread;
        } finally {
            closeIgnoreExceptions(source, sink);
        }
    }


    /**
     * Closes each stream, and more generally each {@link java.io.Closeable} ignoring any {@link java.io.IOException} may occur.
     *
     * @param closeables The {@link java.io.Closeable} objects to close
     */
    public static void closeIgnoreExceptions(Closeable... closeables) {
        for (Closeable closeable : closeables) {
            try {
                closeable.close();
            } catch (IOException ignore) {
            }
        }
    }

    /**
     * Deploys the *.dll and *.sys for the given in a temporary directory.
     *
     * @return The temporary directory absolute path
     * @throws IOException Whenever the deploy process encounters an error
     */
    public static String deployInTempDir() throws IOException {
        File deployDir = createTempDirectory();
        for (String file : new String[]{"WinDivert32.dll", "WinDivert32.sys", "WinDivert64.dll", "WinDivert64.sys"}) {
            File copyFile = new File(deployDir + File.separator + file);
            copyFile.createNewFile();
            copy(ClassLoader.getSystemClassLoader().getResourceAsStream(file), new FileOutputStream(copyFile));
        }
        return deployDir.getAbsolutePath();
    }

    /**
     * Deploys WinDivert DLL and SYS files based upon Platform architecture (32/64bit).
     *
     * @return The {@link WinDivertDLL} instance to use
     */
    public static WinDivertDLL deploy() {
        String jnaLibraryPath = System.getProperty("jna.library.path");
        try {
            System.setProperty("jna.library.path", deployInTempDir());
            return (WinDivertDLL) Native.loadLibrary(Platform.is64Bit() ? "WinDivert64" : "WinDivert32", WinDivertDLL.class);
        } catch (Exception e) {
            throw new ExceptionInInitializerError(new Exception("Unable to deploy WinDivert", e));
        } finally {
            if (jnaLibraryPath != null)
                System.setProperty("jna.library.path", jnaLibraryPath);
        }
    }

}
