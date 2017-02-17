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

package com.github.ffalcinelli.jdivert;

import com.github.ffalcinelli.jdivert.windivert.DeployHandler;
import org.junit.Test;

import java.nio.ByteBuffer;

import static com.github.ffalcinelli.jdivert.Util.parseHexBinary;
import static com.github.ffalcinelli.jdivert.Util.printHexBinary;
import static org.junit.Assert.*;

/**
 * Created by fabio on 26/10/2016.
 */
public class UtilTestCase {

    @Test
    public void coverageHack() {
        /*
         * This simple hack stimulate the coverage of Util and Enums class definition line.
         */
        assertNotNull(new Util());
        assertNotNull(new Enums());
        assertNotNull(new DeployHandler());
    }

    @Test
    public void hexConversion() {
        byte[] bytes = parseHexBinary("0123456789ABCDEF");
        assertEquals("0123456789ABCDEF", printHexBinary(bytes));
        ByteBuffer buffer = ByteBuffer.wrap(bytes);
        assertEquals("0123456789ABCDEF", printHexBinary(buffer));
    }

    @Test(expected = IllegalArgumentException.class)
    public void hexConversionOddCharacters() {
        assertEquals("0123456789ABCDE", printHexBinary(parseHexBinary("0123456789ABCDE")));
    }

    @Test(expected = IllegalArgumentException.class)
    public void hexConversionInvalidCharacters() {
        assertEquals("0123456789ABCDEZ", printHexBinary(parseHexBinary("0123456789ABCDEZ")));
    }

    @Test
    public void zeroPad() {
        byte[] source = new byte[]{0x1, 0x2, 0x3, 0x4};
        assertArrayEquals(new byte[]{0x1, 0x2, 0x3, 0x4, 0x0, 0x0}, Util.zeroPadArray(source, 6));
        assertArrayEquals(new byte[]{0x1, 0x2}, Util.zeroPadArray(source, 2));
    }
}
