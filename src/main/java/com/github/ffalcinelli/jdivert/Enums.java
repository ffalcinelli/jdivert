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

import java.util.stream.Stream;

/**
 * Created by fabio on 20/10/2016.
 */
public class Enums {

    /**
     * See <a href="https://www.reqrypt.org/windivert-doc.html#divert_open">https://www.reqrypt.org/windivert-doc.html#divert_open</a>.
     */
    public enum Layer {
        /**
         * The headers layer. This is the default.
         */
        NETWORK(0),
        /**
         * The headers layer (forwarded packets).
         */
        NETWORK_FORWARD(1),
        FLOW(2),
        SOCKET(3),
        REFLECT(4);
        private int value;

        Layer(int value) {
            this.value = value;
        }

        public int getValue() {
            return value;
        }
    }

    /**
     * See <a href="https://www.reqrypt.org/windivert-doc.html#divert_open">https://www.reqrypt.org/windivert-doc.html#divert_open</a>.
     */
    public enum Flag {
        DEFAULT(0),
        SNIFF(1),
        DROP(2),
        RECV_ONLY(4),
        SEND_ONLY(8),
        NO_INSTALL(16),
        FRAGMENTS(32);
        private int value;

        Flag(int value) {
            this.value = value;
        }

        public int getValue() {
            return value;
        }
    }

    /**
     * See <a href="https://reqrypt.org/windivert-doc.html#divert_set_param">https://reqrypt.org/windivert-doc.html#divert_set_param</a>
     */
    public enum Param {
        QUEUE_LEN(0, 32, 16384, 4096),
        QUEUE_TIME(1, 100, 16000, 2000),
        QUEUE_SIZE(2, 65535, 33554432, 4194304),
        VERSION_MAJOR(3, 0, 99, 2),
        VERSION_MINOR(4, 0, 99, 2);
        private int value;
        private int min;
        private int max;
        private int def;

        Param(int value, int min, int max, int def) {
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

    /**
     * See <a href="https://reqrypt.org/windivert-doc.html#divert_address">https://reqrypt.org/windivert-doc.html#divert_address</a>
     */
    public enum Direction {
        OUTBOUND(0), INBOUND(1);
        private int value;

        Direction(int value) {
            this.value = value;
        }

        public static Direction fromValue(int value) {
            return Stream.of(Direction.values())
                    .filter(d -> d.getValue() == value)
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException(String.format("Direction %d is not recognized", value)));
        }

        public int getValue() {
            return value;
        }
    }

    /**
     * See <a href="https://reqrypt.org/windivert-doc.html#divert_helper_calc_checksums">https://reqrypt.org/windivert-doc.html#divert_helper_calc_checksums</a>
     */
    public enum CalcChecksumsOption {
        /**
         * Do not calculate the Ipv4 checksum.
         */
        NO_IP_CHECKSUM(1),
        /**
         * Do not calculate the Icmp checksum.
         */
        NO_ICMP_CHECKSUM(2),
        /**
         * Do not calculate the Icmpv6 checksum.
         */
        NO_ICMPV6_CHECKSUM(4),
        /**
         * Do not calculate the Tcp checksum.
         */
        NO_TCP_CHECKSUM(8),
        /**
         * Do not calculate the Udp checksum.
         */
        NO_UDP_CHECKSUM(16);
        private int value;

        CalcChecksumsOption(int value) {
            this.value = value;
        }

        public int getValue() {
            return value;
        }
    }

    /**
     * Transport protocol values define the layout of the header that will immediately follow the IPv4 or IPv6 header.
     * See <a href="http://www.iana.org/assignments/protocol-numbers/protocol-numbers.xhtml">http://www.iana.org/assignments/protocol-numbers/protocol-numbers.xhtml</a>
     */
    public enum Protocol {
        HOPOPT(0), ICMP(1), TCP(6), UDP(17), ROUTING(43), FRAGMENT(44), AH(51), ICMPV6(58), NONE(59), DSTOPTS(60);
        private int value;

        Protocol(int value) {
            this.value = value;
        }

        public static Protocol fromValue(int value) {
            return Stream.of(Protocol.values())
                    .filter(p -> p.getValue() == value)
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException(String.format("Protocol %d is not recognized", value)));
        }

        public int getValue() {
            return value;
        }
    }

    /**
     * See <a href="https://reqrypt.org/windivert-doc.html#divert_shutdown">https://reqrypt.org/windivert-doc.html#divert_shutdown</a>
     */
    public enum Shutdown {
        RECV(1), SEND(2), BOTH(3);
        private int value;

        Shutdown(int value) {
            this.value = value;
        }

        public int getValue() {
            return value;
        }
    }
}
