/*
 * Copyright (c) Fabio Falcinelli 2026.
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

package com.github.ffalcinelli.jdivert;

/**
 * JUnit condition for tests that open real handles: Windows (WinDivert, as before) or Linux as
 * root (libebpfdivert needs CAP_BPF and CAP_NET_ADMIN).
 */
public final class CaptureCondition {

    private CaptureCondition() {
    }

    public static boolean canCapture() {
        if (Util.isWindows()) {
            return true;
        }
        return Util.isLinux() && "root".equals(System.getProperty("user.name"));
    }
}
