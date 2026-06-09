package com.github.ffalcinelli.jdivert.ebpfdivert;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Transpiles WinDivert filter strings into eBPF filter rules.
 */
public class FilterTranspiler {

    /**
     * Structure matching ebpfdivert's struct BpfFilterRule
     */
    public static class BpfFilterRule {
        public int protocol; // 0=any, 6=tcp, 17=udp, 1=icmp
        public byte[] src_ip = new byte[16];
        public byte[] dst_ip = new byte[16];
        public int src_port;
        public int dst_port;
        public int match_mask; // Bitmask of which fields to match

        public static final int MATCH_SRC_IP = 1 << 0;
        public static final int MATCH_DST_IP = 1 << 1;
        public static final int MATCH_SRC_PORT = 1 << 2;
        public static final int MATCH_DST_PORT = 1 << 3;
        public static final int MATCH_PROTOCOL = 1 << 4;

        public byte[] toBytes() {
            ByteBuffer bb = ByteBuffer.allocate(48).order(ByteOrder.LITTLE_ENDIAN);
            bb.putInt(protocol);
            bb.put(src_ip);
            bb.put(dst_ip);
            bb.putInt(src_port);
            bb.putInt(dst_port);
            bb.putInt(match_mask);
            return bb.array();
        }
    }

    public static List<BpfFilterRule> transpile(String filter) {
        List<BpfFilterRule> rules = new ArrayList<>();
        if (filter == null || filter.trim().isEmpty() || filter.equalsIgnoreCase("true")) {
            rules.add(new BpfFilterRule()); // Match all
            return rules;
        }

        // Very basic parser for "field == value and field == value"
        // In a real implementation, this would be a proper AST-based parser.
        BpfFilterRule rule = new BpfFilterRule();
        String[] parts = filter.split("(?i)\\s+and\\s+");
        for (String part : parts) {
            parsePart(part.trim(), rule);
        }
        rules.add(rule);
        return rules;
    }

    private static void parsePart(String part, BpfFilterRule rule) {
        Pattern p = Pattern.compile("([\\w\\.]+)\\s*==\\s*([^\\s]+)");
        Matcher m = p.matcher(part);
        if (m.find()) {
            String field = m.group(1).toLowerCase();
            String value = m.group(2);

            try {
                switch (field) {
                    case "ip.srcaddr":
                    case "ipv6.srcaddr":
                        rule.src_ip = InetAddress.getByName(value).getAddress();
                        rule.match_mask |= BpfFilterRule.MATCH_SRC_IP;
                        break;
                    case "ip.dstaddr":
                    case "ipv6.dstaddr":
                        rule.dst_ip = InetAddress.getByName(value).getAddress();
                        rule.match_mask |= BpfFilterRule.MATCH_DST_IP;
                        break;
                    case "tcp.srcport":
                    case "udp.srcport":
                        rule.src_port = Integer.parseInt(value);
                        rule.match_mask |= BpfFilterRule.MATCH_SRC_PORT;
                        rule.protocol = field.startsWith("tcp") ? 6 : 17;
                        rule.match_mask |= BpfFilterRule.MATCH_PROTOCOL;
                        break;
                    case "tcp.dstport":
                    case "udp.dstport":
                        rule.dst_port = Integer.parseInt(value);
                        rule.match_mask |= BpfFilterRule.MATCH_DST_PORT;
                        rule.protocol = field.startsWith("tcp") ? 6 : 17;
                        rule.match_mask |= BpfFilterRule.MATCH_PROTOCOL;
                        break;
                    case "tcp":
                        rule.protocol = 6;
                        rule.match_mask |= BpfFilterRule.MATCH_PROTOCOL;
                        break;
                    case "udp":
                        rule.protocol = 17;
                        rule.match_mask |= BpfFilterRule.MATCH_PROTOCOL;
                        break;
                    case "icmp":
                        rule.protocol = 1;
                        rule.match_mask |= BpfFilterRule.MATCH_PROTOCOL;
                        break;
                }
            } catch (Exception ignored) {
            }
        } else if (part.equalsIgnoreCase("tcp")) {
            rule.protocol = 6;
            rule.match_mask |= BpfFilterRule.MATCH_PROTOCOL;
        } else if (part.equalsIgnoreCase("udp")) {
            rule.protocol = 17;
            rule.match_mask |= BpfFilterRule.MATCH_PROTOCOL;
        } else if (part.equalsIgnoreCase("icmp")) {
            rule.protocol = 1;
            rule.match_mask |= BpfFilterRule.MATCH_PROTOCOL;
        }
    }
}
