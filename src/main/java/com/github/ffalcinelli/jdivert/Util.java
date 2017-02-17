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

import java.nio.ByteBuffer;

/**
 * Created by fabio on 26/10/2016.
 * Gently from http://stackoverflow.com/questions/140131/convert-a-string-representation-of-a-hex-dump-to-a-byte-array-using-java
 * with some enhancement...
 */
public class Util {


    private static final char[] hexCode = "0123456789ABCDEF".toCharArray();

    /**
     * Converts the string argument into an array of bytes.
     *
     * @param s A string containing a binary "hexlified".
     * @return The binary bytes.
     */
    public static byte[] parseHexBinary(String s) {
        final int len = s.length();

        // "111" is not a valid hex encoding.
        if (len % 2 != 0)
            throw new IllegalArgumentException("hexBinary needs to be even-length: " + s);

        byte[] out = new byte[len / 2];

        for (int i = 0; i < len; i += 2) {
            int h = hexToBin(s.charAt(i));
            int l = hexToBin(s.charAt(i + 1));
            if (h == -1 || l == -1)
                throw new IllegalArgumentException("contains illegal character for hexBinary: " + s);

            out[i / 2] = (byte) (h * 16 + l);
        }

        return out;
    }

    /**
     * Convert a character to it's hex value.
     *
     * @param ch The character [a-fA-F0-9].
     * @return An integer representing the hex value.
     */
    private static int hexToBin(char ch) {
        if ('0' <= ch && ch <= '9') return ch - '0';
        if ('A' <= ch && ch <= 'F') return ch - 'A' + 10;
        if ('a' <= ch && ch <= 'f') return ch - 'a' + 10;
        return -1;
    }

    /**
     * "Hexlify" a {@link java.nio.ByteBuffer} into a string.
     *
     * @param data The binary data to convert.
     * @return The "hexlified" representation of data.
     */
    public static String printHexBinary(ByteBuffer data) {
        return printHexBinary(getBytesAtOffset(data, 0, data.capacity()));
    }

    /**
     * Converts an array of bytes into a string.
     *
     * @param data The binary data to convert
     * @return The "hexlified" representation of data
     */
    public static String printHexBinary(byte[] data) {
        StringBuilder r = new StringBuilder(data.length * 2);
        for (byte b : data) {
            r.append(hexCode[(b >> 4) & 0xF]);
            r.append(hexCode[(b & 0xF)]);
        }
        return r.toString();
    }

    /**
     * Pad a byte array with zeroes. If Source array length exceed size, then truncate it.
     *
     * @param source The source array.
     * @param size   The final array size.
     * @return The array of size elements eventually zero-padded.
     */
    public static byte[] zeroPadArray(byte[] source, int size) {
        byte[] data = new byte[size];
        for (int i = 0; i < data.length; i++) {
            if (i < source.length)
                data[i] = source[i];
            else
                data[i] = 0;
        }
        return data;
    }

    /**
     * Given a {@link java.nio.ByteBuffer} get bytes in an absolute offset without altering its current position.
     * <p>
     * The operation is {@code synchronized} over the {@code buffer} to avoid thread interference.
     *
     * @param buffer The {@link java.nio.ByteBuffer} to read data from.
     * @param offset The absolute offset from which starting to extract data.
     * @param length How many bytes to extract.
     * @return The exctracted bytes as a byte array.
     */
    public static byte[] getBytesAtOffset(ByteBuffer buffer, int offset, int length) {
        byte[] data = new byte[length];
        synchronized (buffer) {
            int position = buffer.position();
            buffer.position(offset);
            buffer.get(data);
            buffer.position(position);
        }
        return data;
    }

    /**
     * Given a {@link java.nio.ByteBuffer} set bytes in an absolute offset without altering its current position.
     * <p>
     * The operation is {@code synchronized} over the {@code buffer} to avoid thread interference.
     *
     * @param buffer The {@link java.nio.ByteBuffer} to write data to.
     * @param offset The absolute offset from which starting to write data.
     * @param length How many bytes to write.
     * @param data   The data to write into the {@link java.nio.ByteBuffer}.
     */
    public static void setBytesAtOffset(ByteBuffer buffer, int offset, int length, byte[] data) {
        synchronized (buffer) {
            int position = buffer.position();
            buffer.position(offset);
            buffer.put(data, 0, length);
            buffer.position(position);
        }
    }

    /**
     * Convert a short into its unsigned representation as int.
     *
     * @param value The value to convert.
     * @return The unsigned representation.
     */
    public static int unsigned(short value) {
        return value & 0x0000ffff;
    }

    /**
     * Convert a byte into its unsigned representation as int.
     *
     * @param value The value to convert.
     * @return The unsigned representation.
     */
    public static int unsigned(byte value) {
        return value & 0x000000ff;
    }
}
