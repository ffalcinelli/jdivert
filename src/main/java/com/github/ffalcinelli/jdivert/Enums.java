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
        NETWORK_FORWARD(1);
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
        /**
         * This flag opens the WinDivert handle in packet sniffing mode. In packet sniffing mode the original packet is not dropped-and-diverted (the default) but copied-and-diverted. This mode is useful for implementing packet sniffing tools similar to those applications that currently use Winpcap.
         */
        SNIFF(1),
        /**
         * This flag indicates that the user application does not intend to read matching packets with WinDivertRecv(), instead the packets should be silently dropped. This is useful for implementing simple packet filters using the <a href="https://www.reqrypt.org/windivert-doc.html#filter_language">WinDivert filter language</a>.
         */
        DROP(2),
        /**
         * By default WinDivert ensures that each diverted packet has a valid checksum. If the checksum is missing (e.g. with Tcp checksum offloading), WinDivert will calculate it before passing the packet to the user application. This flag disables this behavior.
         */
        NO_CHECKSUM(1024);
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
        /**
         * Sets the maximum length of the packet queue for {@link WinDivert#recv()}. Currently the default value is 512 (actually 1024), the minimum is 1, and the maximum is 8192.
         */
        QUEUE_LEN(0, 1, 8192, 1024 /* but docs state 512 */),
        /**
         * Sets the minimum time, in milliseconds, a packet can be queued before it is automatically dropped. Packets cannot be queued indefinitely, and ideally, packets should be processed by the application as soon as is possible. Note that this sets the minimum time a packet can be queued before it can be dropped. The actual time may be exceed this value. Currently the default value is 512, the minimum is 128, and the maximum is 2048.
         */
        QUEUE_TIME(1, 128, 2048, 512);
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
            //works because values --> ordinal 0/1
            return Direction.values()[value];
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
