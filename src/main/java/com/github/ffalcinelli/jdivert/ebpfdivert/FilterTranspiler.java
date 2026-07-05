package com.github.ffalcinelli.jdivert.ebpfdivert;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Transpiles WinDivert filter strings into eBPF filter rules.
 */
public class FilterTranspiler {

    public static final short MATCH_SRC_IP = 1 << 0;
    public static final short MATCH_DST_IP = 1 << 1;
    public static final short MATCH_SRC_PORT = 1 << 2;
    public static final short MATCH_DST_PORT = 1 << 3;
    public static final short MATCH_PROTOCOL = 1 << 4;
    public static final short MATCH_DIRECTION = 1 << 5;
    public static final short MATCH_LOOPBACK = 1 << 6;
    public static final short MATCH_FALSE = 1 << 7;
    public static final short MATCH_ENABLED = 1 << 8;
    public static final short MATCH_SNIFF = 1 << 9;
    public static final short MATCH_DROP = 1 << 10;
    public static final short MATCH_TTL = 1 << 11;
    public static final short MATCH_TCP_FLAGS = 1 << 12;

    public static class TranspiledRule {
        public boolean isIpv6;
        public byte[] ruleBytes;

        public TranspiledRule(boolean isIpv6, byte[] ruleBytes) {
            this.isIpv6 = isIpv6;
            this.ruleBytes = ruleBytes;
        }
    }

    public static class BpfFilterRule {
        public int src_ip;
        public int dst_ip;
        public int src_mask;
        public int dst_mask;
        public short src_port_start;
        public short src_port_end;
        public short dst_port_start;
        public short dst_port_end;
        public short match_mask = MATCH_ENABLED;
        public short invert_mask;
        public byte proto;
        public byte direction;
        public byte loopback;
        public byte ttl;
        public byte tcp_flags;
        public byte tcp_flags_mask;

        public byte[] toBytes() {
            ByteBuffer bb = ByteBuffer.allocate(34).order(ByteOrder.LITTLE_ENDIAN);
            bb.putInt(src_ip);
            bb.putInt(dst_ip);
            bb.putInt(src_mask);
            bb.putInt(dst_mask);
            bb.putShort(src_port_start);
            bb.putShort(src_port_end);
            bb.putShort(dst_port_start);
            bb.putShort(dst_port_end);
            bb.putShort(match_mask);
            bb.putShort(invert_mask);
            bb.put(proto);
            bb.put(direction);
            bb.put(loopback);
            bb.put(ttl);
            bb.put(tcp_flags);
            bb.put(tcp_flags_mask);
            return bb.array();
        }
    }

    public static class BpfFilterRuleIpv6 {
        public byte[] src_ip = new byte[16];
        public byte[] dst_ip = new byte[16];
        public byte[] src_mask = new byte[16];
        public byte[] dst_mask = new byte[16];
        public short src_port_start;
        public short src_port_end;
        public short dst_port_start;
        public short dst_port_end;
        public short match_mask = MATCH_ENABLED;
        public short invert_mask;
        public byte proto;
        public byte direction;
        public byte loopback;
        public byte ttl;
        public byte tcp_flags;
        public byte tcp_flags_mask;

        public byte[] toBytes() {
            ByteBuffer bb = ByteBuffer.allocate(82).order(ByteOrder.LITTLE_ENDIAN);
            bb.put(src_ip);
            bb.put(dst_ip);
            bb.put(src_mask);
            bb.put(dst_mask);
            bb.putShort(src_port_start);
            bb.putShort(src_port_end);
            bb.putShort(dst_port_start);
            bb.putShort(dst_port_end);
            bb.putShort(match_mask);
            bb.putShort(invert_mask);
            bb.put(proto);
            bb.put(direction);
            bb.put(loopback);
            bb.put(ttl);
            bb.put(tcp_flags);
            bb.put(tcp_flags_mask);
            return bb.array();
        }
    }

    public static List<TranspiledRule> transpile(String filter, boolean sniff, boolean drop) {
        List<TranspiledRule> rules = new ArrayList<>();
        if (filter == null || filter.trim().isEmpty() || filter.equalsIgnoreCase("true")) {
            // Default Match All
            BpfFilterRule rule = new BpfFilterRule();
            if (sniff) rule.match_mask |= MATCH_SNIFF;
            if (drop) rule.match_mask |= MATCH_DROP;
            rules.add(new TranspiledRule(false, rule.toBytes()));
            return rules;
        }

        if (filter.trim().equalsIgnoreCase("false")) {
            BpfFilterRule rule = new BpfFilterRule();
            rule.match_mask |= MATCH_FALSE;
            rules.add(new TranspiledRule(false, rule.toBytes()));
            return rules;
        }

        // We support simple expressions separated by OR or comma.
        // For simplicity, we split on OR/|| to generate separate rules.
        String[] orParts = filter.split("(?i)\\s+(or|\\|\\|)\\s+");
        for (String orPart : orParts) {
            String[] parts = orPart.split("(?i)\\s+(and|&&)\\s+");
            
            // Temporary parse structures
            boolean isIpv6 = false;
            
            // Check if any part implies IPv6
            for (String part : parts) {
                String p = part.trim().toLowerCase();
                if (p.contains("ipv6") || p.contains("icmpv6") || (p.contains(":") && p.contains("=="))) {
                    isIpv6 = true;
                    break;
                }
            }

            if (isIpv6) {
                BpfFilterRuleIpv6 rule = new BpfFilterRuleIpv6();
                if (sniff) rule.match_mask |= MATCH_SNIFF;
                if (drop) rule.match_mask |= MATCH_DROP;
                for (String part : parts) {
                    parsePartIpv6(part.trim(), rule);
                }
                rules.add(new TranspiledRule(true, rule.toBytes()));
            } else {
                BpfFilterRule rule = new BpfFilterRule();
                if (sniff) rule.match_mask |= MATCH_SNIFF;
                if (drop) rule.match_mask |= MATCH_DROP;
                for (String part : parts) {
                    parsePartIpv4(part.trim(), rule);
                }
                rules.add(new TranspiledRule(false, rule.toBytes()));
            }
        }
        return rules;
    }

    private static byte[] cidrToIpv6Mask(int prefix) {
        byte[] mask = new byte[16];
        int bits = prefix;
        for (int i = 0; i < 16; i++) {
            if (bits >= 8) {
                mask[i] = (byte) 0xFF;
                bits -= 8;
            } else if (bits > 0) {
                mask[i] = (byte) (0xFF << (8 - bits));
                bits = 0;
            } else {
                mask[i] = 0;
            }
        }
        return mask;
    }

    private static void parsePartIpv4(String part, BpfFilterRule rule) {
        String p = part.toLowerCase();
        if (p.equals("tcp")) {
            rule.proto = 6;
            rule.match_mask |= MATCH_PROTOCOL;
            return;
        }
        if (p.equals("udp")) {
            rule.proto = 17;
            rule.match_mask |= MATCH_PROTOCOL;
            return;
        }
        if (p.equals("icmp")) {
            rule.proto = 1;
            rule.match_mask |= MATCH_PROTOCOL;
            return;
        }
        if (p.equals("inbound")) {
            rule.direction = 1;
            rule.match_mask |= MATCH_DIRECTION;
            return;
        }
        if (p.equals("outbound")) {
            rule.direction = 2;
            rule.match_mask |= MATCH_DIRECTION;
            return;
        }
        if (p.equals("loopback")) {
            rule.loopback = 1;
            rule.match_mask |= MATCH_LOOPBACK;
            return;
        }
        if (p.equals("tcp.syn")) {
            rule.proto = 6;
            rule.match_mask |= MATCH_PROTOCOL | MATCH_TCP_FLAGS;
            rule.tcp_flags = 0x02;
            rule.tcp_flags_mask = 0x02;
            return;
        }

        Pattern pat = Pattern.compile("([\\w\\.]+)\\s*(!?=|[<>]=?)\\s*([^\\s]+)");
        Matcher m = pat.matcher(part);
        if (m.find()) {
            String field = m.group(1).toLowerCase();
            String op = m.group(2);
            String value = m.group(3);

            boolean invert = op.equals("!=");

            try {
                if (field.equals("ip.srcaddr") || field.equals("ip.src")) {
                    rule.match_mask |= MATCH_SRC_IP;
                    if (invert) rule.invert_mask |= MATCH_SRC_IP;
                    String[] parts = value.split("/");
                    String ip = parts[0];
                    int prefix = parts.length > 1 ? Integer.parseInt(parts[1]) : 32;
                    rule.src_ip = ByteBuffer.wrap(InetAddress.getByName(ip).getAddress()).getInt();
                    rule.src_mask = prefix == 0 ? 0 : (0xFFFFFFFF << (32 - prefix));
                } else if (field.equals("ip.dstaddr") || field.equals("ip.dst")) {
                    rule.match_mask |= MATCH_DST_IP;
                    if (invert) rule.invert_mask |= MATCH_DST_IP;
                    String[] parts = value.split("/");
                    String ip = parts[0];
                    int prefix = parts.length > 1 ? Integer.parseInt(parts[1]) : 32;
                    rule.dst_ip = ByteBuffer.wrap(InetAddress.getByName(ip).getAddress()).getInt();
                    rule.dst_mask = prefix == 0 ? 0 : (0xFFFFFFFF << (32 - prefix));
                } else if (field.equals("tcp.srcport") || field.equals("udp.srcport")) {
                    rule.match_mask |= MATCH_SRC_PORT;
                    if (invert) rule.invert_mask |= MATCH_SRC_PORT;
                    rule.proto = (byte)(field.startsWith("tcp") ? 6 : 17);
                    rule.match_mask |= MATCH_PROTOCOL;
                    if (value.contains("-")) {
                        String[] range = value.split("-");
                        rule.src_port_start = (short)Integer.parseInt(range[0]);
                        rule.src_port_end = (short)Integer.parseInt(range[1]);
                    } else {
                        short prt = (short)Integer.parseInt(value);
                        rule.src_port_start = prt;
                        rule.src_port_end = prt;
                    }
                } else if (field.equals("tcp.dstport") || field.equals("udp.dstport")) {
                    rule.match_mask |= MATCH_DST_PORT;
                    if (invert) rule.invert_mask |= MATCH_DST_PORT;
                    rule.proto = (byte)(field.startsWith("tcp") ? 6 : 17);
                    rule.match_mask |= MATCH_PROTOCOL;
                    if (value.contains("-")) {
                        String[] range = value.split("-");
                        rule.dst_port_start = (short)Integer.parseInt(range[0]);
                        rule.dst_port_end = (short)Integer.parseInt(range[1]);
                    } else {
                        short prt = (short)Integer.parseInt(value);
                        rule.dst_port_start = prt;
                        rule.dst_port_end = prt;
                    }
                } else if (field.equals("ttl")) {
                    rule.match_mask |= MATCH_TTL;
                    if (invert) rule.invert_mask |= MATCH_TTL;
                    rule.ttl = (byte)Integer.parseInt(value);
                }
            } catch (Exception ignored) {
            }
        }
    }

    private static void parsePartIpv6(String part, BpfFilterRuleIpv6 rule) {
        String p = part.toLowerCase();
        if (p.equals("tcp")) {
            rule.proto = 6;
            rule.match_mask |= MATCH_PROTOCOL;
            return;
        }
        if (p.equals("udp")) {
            rule.proto = 17;
            rule.match_mask |= MATCH_PROTOCOL;
            return;
        }
        if (p.equals("icmpv6")) {
            rule.proto = 58;
            rule.match_mask |= MATCH_PROTOCOL;
            return;
        }
        if (p.equals("inbound")) {
            rule.direction = 1;
            rule.match_mask |= MATCH_DIRECTION;
            return;
        }
        if (p.equals("outbound")) {
            rule.direction = 2;
            rule.match_mask |= MATCH_DIRECTION;
            return;
        }
        if (p.equals("loopback")) {
            rule.loopback = 1;
            rule.match_mask |= MATCH_LOOPBACK;
            return;
        }
        if (p.equals("tcp.syn")) {
            rule.proto = 6;
            rule.match_mask |= MATCH_PROTOCOL | MATCH_TCP_FLAGS;
            rule.tcp_flags = 0x02;
            rule.tcp_flags_mask = 0x02;
            return;
        }

        Pattern pat = Pattern.compile("([\\w\\.]+)\\s*([!=<>]=?)\\s*([^\\s]+)");
        Matcher m = pat.matcher(part);
        if (m.find()) {
            String field = m.group(1).toLowerCase();
            String op = m.group(2);
            String value = m.group(3);

            boolean invert = op.equals("!=");

            try {
                if (field.equals("ipv6.srcaddr") || field.equals("ipv6.src") || field.equals("ip.srcaddr") || field.equals("ip.src")) {
                    rule.match_mask |= MATCH_SRC_IP;
                    if (invert) rule.invert_mask |= MATCH_SRC_IP;
                    String[] parts = value.split("/");
                    String ip = parts[0];
                    int prefix = parts.length > 1 ? Integer.parseInt(parts[1]) : 128;
                    rule.src_ip = InetAddress.getByName(ip).getAddress();
                    rule.src_mask = cidrToIpv6Mask(prefix);
                } else if (field.equals("ipv6.dstaddr") || field.equals("ipv6.dst") || field.equals("ip.dstaddr") || field.equals("ip.dst")) {
                    rule.match_mask |= MATCH_DST_IP;
                    if (invert) rule.invert_mask |= MATCH_DST_IP;
                    String[] parts = value.split("/");
                    String ip = parts[0];
                    int prefix = parts.length > 1 ? Integer.parseInt(parts[1]) : 128;
                    rule.dst_ip = InetAddress.getByName(ip).getAddress();
                    rule.dst_mask = cidrToIpv6Mask(prefix);
                } else if (field.equals("tcp.srcport") || field.equals("udp.srcport")) {
                    rule.match_mask |= MATCH_SRC_PORT;
                    if (invert) rule.invert_mask |= MATCH_SRC_PORT;
                    rule.proto = (byte)(field.startsWith("tcp") ? 6 : 17);
                    rule.match_mask |= MATCH_PROTOCOL;
                    if (value.contains("-")) {
                        String[] range = value.split("-");
                        rule.src_port_start = (short)Integer.parseInt(range[0]);
                        rule.src_port_end = (short)Integer.parseInt(range[1]);
                    } else {
                        short prt = (short)Integer.parseInt(value);
                        rule.src_port_start = prt;
                        rule.src_port_end = prt;
                    }
                } else if (field.equals("tcp.dstport") || field.equals("udp.dstport")) {
                    rule.match_mask |= MATCH_DST_PORT;
                    if (invert) rule.invert_mask |= MATCH_DST_PORT;
                    rule.proto = (byte)(field.startsWith("tcp") ? 6 : 17);
                    rule.match_mask |= MATCH_PROTOCOL;
                    if (value.contains("-")) {
                        String[] range = value.split("-");
                        rule.dst_port_start = (short)Integer.parseInt(range[0]);
                        rule.dst_port_end = (short)Integer.parseInt(range[1]);
                    } else {
                        short prt = (short)Integer.parseInt(value);
                        rule.dst_port_start = prt;
                        rule.dst_port_end = prt;
                    }
                } else if (field.equals("ttl")) {
                    rule.match_mask |= MATCH_TTL;
                    if (invert) rule.invert_mask |= MATCH_TTL;
                    rule.ttl = (byte)Integer.parseInt(value);
                }
            } catch (Exception ignored) {
            }
        }
    }
}
