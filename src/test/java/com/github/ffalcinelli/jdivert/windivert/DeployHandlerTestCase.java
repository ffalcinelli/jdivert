/*
 * Copyright (c) Fabio Falcinelli 2017.
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

import org.junit.Test;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

/**
 * Created by fabio on 17/02/2017.
 */
public class DeployHandlerTestCase {

    @Test
    public void closeIgnoreExceptions() {
        try {
            DeployHandler.closeIgnoreExceptions(new Closeable() {
                @Override
                public void close() throws IOException {
                    throw new IOException("Fake!");
                }
            });
        } catch (Exception e) {
            fail("No exceptions should be thrown in closing Closeables");
        }
    }

    @Test(expected = ExceptionInInitializerError.class)
    public void exceptionInInitializer() {
        DeployHandler.deploy(new TemporaryDirManager() {
            @Override
            public File createTempDir() throws IOException {
                return null;
            }
        });
    }

    @Test
    public void restoreJnaLibraryPathAfterDeploy() {
        String jnaLibraryPath = "some_path";
        System.setProperty("jna.library.path", jnaLibraryPath);
        DeployHandler.deploy();
        assertEquals(jnaLibraryPath, System.getProperty("jna.library.path"));
    }
}
