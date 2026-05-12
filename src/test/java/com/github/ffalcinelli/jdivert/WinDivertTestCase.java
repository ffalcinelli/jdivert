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

package com.github.ffalcinelli.jdivert;

import com.github.ffalcinelli.jdivert.exceptions.WinDivertException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Random;

import static com.github.ffalcinelli.jdivert.Enums.Flag.DEFAULT;
import static com.github.ffalcinelli.jdivert.Enums.Flag.DROP;
import static com.github.ffalcinelli.jdivert.Enums.Flag.FRAGMENTS;
import static com.github.ffalcinelli.jdivert.Enums.Flag.SNIFF;
import static com.github.ffalcinelli.jdivert.Enums.Layer.NETWORK;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Created by fabio on 26/10/2016.
 */
public class WinDivertTestCase {

    //this can be safely static
    static Random rnd = new Random();
    protected WinDivert w;

    public static int randInt(int min, int max) {
        return rnd.nextInt(max - min + 1) + min;
    }

    @AfterEach
    public void tearDown() throws WinDivertException {
        if (w != null)
            w.close();
    }

    @Test
    public void wrongFlags() {
        assertThrows(IllegalArgumentException.class, () -> w = new WinDivert("false", NETWORK, 0, SNIFF, DROP));
    }

    @Test
    public void open() throws WinDivertException {
        w = new WinDivert("false");
        assertEquals(w, w.open());
        assertTrue(w.isOpen());
        assertTrue(w.toString().contains("state=OPEN"));
        w.close();
        assertFalse(w.isOpen());
    }

    @Test
    public void reOpen() throws WinDivertException {
        w = new WinDivert("false");
        w.open();
        assertTrue(w.isOpen());
        assertThrows(IllegalStateException.class, () -> w.open());
    }

    @Test
    public void setParamWindivertNotOpen() {
        w = new WinDivert("false");
        for (Enums.Param param : Enums.Param.values()) {
            assertThrows(IllegalStateException.class, () -> w.setParam(param, param.getDefault()));
        }
    }

    @Test
    public void getParamWindivertNotOpen() {
        w = new WinDivert("false");
        for (Enums.Param param : Enums.Param.values()) {
            assertThrows(IllegalStateException.class, () -> w.getParam(param));
        }
    }


    @Test
    public void params() throws WinDivertException {
        w = new WinDivert("false").open();
        for (Enums.Param param : Enums.Param.values()) {
            assertEquals(param.getDefault(), w.getParam(param), param.toString());
            if (param.isReadOnly()) {
                continue;
            }
            long value = randInt(param.getMin(), param.getMax());
            w.setParam(param, value);
            long newValue = w.getParam(param);
            assertTrue(newValue >= param.getMin() && newValue <= param.getMax(), param + " value: " + newValue);

            assertThrows(IllegalArgumentException.class, () -> w.setParam(param, param.getMax() + 1),
                    String.format("%s is out of min range, but no exception has been thrown.", param));

            assertThrows(IllegalArgumentException.class, () -> w.setParam(param, param.getMin() - 1),
                    String.format("%s is out of max range, but no exception has been thrown.", param));
        }
    }

    @Test
    public void sniffAndDropError() {
        assertThrows(IllegalArgumentException.class, () -> new WinDivert("true", Enums.Layer.NETWORK, 0, Enums.Flag.SNIFF, Enums.Flag.DROP));
    }

    @Test
    public void testGetMode() {
        w = new WinDivert("true", Enums.Layer.NETWORK, 0, Enums.Flag.SNIFF);
        assertEquals("SNIFF", w.getMode());
        
        w = new WinDivert("true", Enums.Layer.NETWORK, 0, Enums.Flag.DROP);
        assertEquals("DROP", w.getMode());
        
        w = new WinDivert("true", Enums.Layer.NETWORK, 0);
        assertEquals("DEFAULT", w.getMode());
    }

    @Test
    public void testToString() {
        w = new WinDivert("true");
        String s = w.toString();
        assertNotNull(s);
        assertTrue(s.contains("filter=true"));
        assertTrue(s.contains("state=CLOSED"));
    }

    @Test
    public void wrongFilterSyntax() {
        WinDivertException e = assertThrows(WinDivertException.class, () -> w = new WinDivert("something").open());
        assertEquals(87, e.getCode());
        assertTrue(e.toString().contains("code=87"));
    }

    @Test
    public void flags() {
        w = new WinDivert("false");
        assertTrue(w.is(DEFAULT));
        assertFalse(w.is(SNIFF));
        assertFalse(w.is(DROP));
        assertFalse(w.is(FRAGMENTS));
        assertTrue(w.toString().contains("mode=DEFAULT"));
    }

}
