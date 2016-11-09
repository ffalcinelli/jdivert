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

import com.github.ffalcinelli.jdivert.exceptions.WinDivertException;
import org.junit.After;
import org.junit.Test;

import java.util.Random;

import static com.github.ffalcinelli.jdivert.Enums.Flag.DROP;
import static com.github.ffalcinelli.jdivert.Enums.Flag.SNIFF;
import static com.github.ffalcinelli.jdivert.Enums.Layer.NETWORK;
import static org.junit.Assert.*;

/**
 * Created by fabio on 26/10/2016.
 */
public class WinDivertTestCase {

    protected WinDivert w;
    //this can be safely static
    static Random rnd = new Random();

    public static int randInt(int min, int max) {
        return rnd.nextInt(max - min + 1) + min;
    }

    @After
    public void tearDown() {
        if (w != null && w.isOpen()) {
            try {
                w.close();
            } catch (WinDivertException ignore) {
            }
        }
    }

    @Test(expected = IllegalArgumentException.class)
    public void wrongFlags() {
        w = new WinDivert("true", NETWORK, 0, SNIFF, DROP);
    }

    @Test
    public void open() throws WinDivertException {
        w = new WinDivert("true");
        assertEquals(w, w.open());
        assertTrue(w.isOpen());
        w.close();
        assertFalse(w.isOpen());
    }

    @Test(expected = IllegalStateException.class)
    public void reOpen() throws WinDivertException {
        w = new WinDivert("true");
        w.open();
        assertTrue(w.isOpen());
        w.open();
    }

    @Test(expected = IllegalStateException.class)
    public void setParamWindivertNotOpen() {
        w = new WinDivert("true");
        for (Enums.Param param : Enums.Param.values()) {
            // I know this won't iterate...
            w.setParam(param, param.getDefault());
        }
    }

    @Test(expected = IllegalStateException.class)
    public void getParamWindivertNotOpen() {
        w = new WinDivert("true");
        for (Enums.Param param : Enums.Param.values()) {
            // I know this won't iterate...
            assertEquals(w.getParam(param), param.getDefault());
        }
    }


    @Test
    public void params() throws WinDivertException {
        w = new WinDivert("true").open();
        for (Enums.Param param : Enums.Param.values()) {
            assertEquals(param.toString(), param.getDefault(), w.getParam(param));
            long value = randInt(param.getMin(), param.getMax());
            w.setParam(param, value);
            assertEquals(param.toString(), value, w.getParam(param));

            try {
                w.setParam(param, param.getMax() + 1);
                fail(String.format("%s is out of min range, but no exception has been thrown.",
                        param));
            } catch (IllegalArgumentException e) {
            }

            try {
                w.setParam(param, param.getMin() - 1);
                fail(String.format("%s is out of max range, but no exception has been thrown.",
                        param));
            } catch (IllegalArgumentException e) {
            }
        }
    }

    @Test(expected = WinDivertException.class)
    public void wrongFilterSyntax() throws WinDivertException {
        try {
            w = new WinDivert("something").open();
        } catch (WinDivertException e) {
            assertEquals(87, e.getCode());
            assertTrue(e.toString().contains("code=87"));
            throw e;
        }
    }

}
