# WinDivert Filter Language

The WinDivert filter language is a powerful, kernel-mode expression language used to specify which network packets should be captured by JDivert. Filters are executed within the Windows Filtering Platform (WFP) for maximum performance.

## Basic Syntax

A filter is a boolean expression that evaluates to `true` or `false` for each packet.

-   **Constants**: `true` (matches everything), `false` (matches nothing).
-   **Fields**: Protocol-specific fields (e.g., `ip.SrcAddr`, `tcp.DstPort`).
-   **Operators**:
    -   Logical: `&&` (AND), `||` (OR), `!` (NOT).
    -   Relational: `==`, `!=`, `<`, `>`, `<=`, `>=`.
-   **Parentheses**: Used for grouping expressions: `(tcp && tcp.DstPort == 80) || udp`.

## Common Fields

### 1. IPv4 Fields (`ip`)
| Field | Description | Example |
| :--- | :--- | :--- |
| `ip.SrcAddr` | Source IPv4 address | `ip.SrcAddr == 192.168.1.1` |
| `ip.DstAddr` | Destination IPv4 address | `ip.DstAddr == 8.8.8.8` |
| `ip.Protocol` | Protocol number (TCP=6, UDP=17, ICMP=1) | `ip.Protocol == 17` |
| `ip.TTL` | Time to Live | `ip.TTL < 64` |

### 2. IPv6 Fields (`ipv6`)
| Field | Description | Example |
| :--- | :--- | :--- |
| `ipv6.SrcAddr` | Source IPv6 address | `ipv6.SrcAddr == fe80::...` |
| `ipv6.DstAddr` | Destination IPv6 address | `ipv6.DstAddr == ::1` |
| `ipv6.NextHeader` | Next header protocol | `ipv6.NextHeader == 6` |

### 3. TCP Fields (`tcp`)
| Field | Description | Example |
| :--- | :--- | :--- |
| `tcp.SrcPort` | Source TCP port | `tcp.SrcPort == 443` |
| `tcp.DstPort` | Destination TCP port | `tcp.DstPort == 80` |
| `tcp.Flags` | TCP flags | `tcp.Syn && !tcp.Ack` |
| `tcp.Syn`, `tcp.Ack`, `tcp.Rst`, `tcp.Fin`, `tcp.Psh`, `tcp.Urg` | Individual flag booleans | `tcp.Rst || tcp.Fin` |

### 4. UDP Fields (`udp`)
| Field | Description | Example |
| :--- | :--- | :--- |
| `udp.SrcPort` | Source UDP port | `udp.SrcPort == 53` |
| `udp.DstPort` | Destination UDP port | `udp.DstPort == 123` |

### 5. ICMP/ICMPv6 Fields (`icmp` / `icmpv6`)
| Field | Description | Example |
| :--- | :--- | :--- |
| `icmp.Type` | ICMP message type | `icmp.Type == 8` (Echo Request) |
| `icmp.Code` | ICMP message code | `icmp.Code == 0` |

## Direction & Interface

You can filter based on the packet's direction relative to the network stack.

-   **`inbound`**: Packets arriving from the network.
-   **`outbound`**: Packets sent by the local system.
-   **`loopback`**: Packets sent to/from the local system.
-   **`ifIdx`**: The network interface index.

Example: `outbound && ip.DstAddr == 1.2.3.4`

## Practical Examples

-   **HTTP Traffic**: `tcp.DstPort == 80 || tcp.SrcPort == 80`
-   **DNS Queries (UDP)**: `udp.DstPort == 53`
-   **Block All Outbound except HTTPS**: `outbound && tcp.DstPort != 443`
-   **Monitor Local Traffic**: `loopback && !icmp`

## Advanced: FLOW & SOCKET Layers

When using `Layer.FLOW` or `Layer.SOCKET`, the available fields change to reflect connection metadata rather than individual packet headers.

-   **FLOW**: Fields like `flow.LocalAddr`, `flow.RemotePort`, `flow.Protocol`.
-   **SOCKET**: Fields like `socket.ProcessId`, `socket.LocalPort`.

For a full, exhaustive list of fields, refer to the [Official WinDivert Documentation](https://reqrypt.org/windivert-doc.html#filter_language).
