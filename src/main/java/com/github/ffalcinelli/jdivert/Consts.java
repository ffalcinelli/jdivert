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

/**
 * Created by fabio on 20/10/2016.
 */
public class Consts {

    public enum Layer {
        NETWORK(0), NETWORK_FORWARD(1);
        private int value;

        private Layer(int value) {
            this.value = value;
        }

        public int getValue() {
            return value;
        }
    }

    public enum Flag {
        SNIFF(1), DROP(2), NO_CHECKSUM(1024);
        private int value;

        private Flag(int value) {
            this.value = value;
        }

        public int getValue() {
            return value;
        }
    }

    public enum Param {
        QUEUE_LEN(0, 1, 8192, 1024 /* but docs state 512 */), QUEUE_TIME(1, 128, 2048, 512);
        private int value;
        private int min;
        private int max;
        private int def;

        private Param(int value, int min, int max, int def) {
            this.value = value;
            this.min = min;
            this.max = max;
            this.def = def;
        }

        public int getValue() {
            return value;
        }

        public int getMin() {
            return min;
        }

        public int getMax() {
            return max;
        }

        public int getDefault() {
            return def;
        }
    }

    public static enum Direction {
        OUTBOUND(0), INBOUND(1);
        private int value;

        private Direction(int value) {
            this.value = value;
        }

        public static Direction fromValue(int value) {
            //works because values --> ordinal 0/1
            return Direction.values()[value];
        }

        public int getValue() {
            return value;
        }
    }

    public enum CalcChecksumsOption {
        NO_IP_CHECKSUM(1), NO_ICMP_CHECKSUM(2), NO_ICMPV6_CHECKSUM(4), NO_TCP_CHECKSUM(8), NO_UDP_CHECKSUM(16);
        private int value;

        private CalcChecksumsOption(int value) {
            this.value = value;
        }

        public int getValue() {
            return value;
        }
    }

    public enum Protocol {
        HOPOPT(0), ICMP(1), TCP(6), UDP(17), ROUTING(43), FRAGMENT(44), AH(51), ICMPV6(58), NONE(59), DSTOPTS(60);
        private int value;

        private Protocol(int value) {
            this.value = value;
        }

        public static Protocol fromValue(int value) {
            for (Protocol protocol : Protocol.values()) {
                if (protocol.getValue() == value)
                    return protocol;
            }
            throw new IllegalArgumentException(String.format("Protocol %d is not recognized", value));
        }

        public int getValue() {
            return value;
        }
    }
}
