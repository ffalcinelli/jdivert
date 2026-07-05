# Linux (eBPF) Backend Guide

JDivert supports packet capture and injection on **Linux** using **eBPF (TC subclass hooks)**. This enables developer parity with the Windows/WinDivert backend by reusing the same filter language and API paradigms.

> [!WARNING]
> Linux support via eBPF is experimental and should not be used in production environments.

---

## 1. Native Adapters: Panama vs JNA

JDivert automatically chooses the most performant native adapter at runtime based on the Java version and available environment:

- **Panama Adapter (`EBPFDivertPanamaNativeAdapter`)**:
  - Automatically enabled on **Java 22+**.
  - Uses the JDK’s native Foreign Function & Memory API (Project Panama) to invoke kernel and `libbpf` functions with zero overhead.
- **JNA Adapter (`EBPFDivertJnaNativeAdapter`)**:
  - Used on **Java 8 through 21**.
  - Relies on Java Native Access (JNA) to map to standard `libc` and `libbpf` shared libraries.

---

## 2. Zero-Install Deployment on Linux

Like its Windows counterpart, JDivert bundles the required eBPF driver payload within the JAR resources:
- During driver initialization, `DeployHandler` detects the Linux OS and extracts `ebpfdivert.bpf.o` to a temporary version-specific directory (e.g., `/tmp/jdivert-3.0.0/`).
- It maps the library path so that the local `libbpf` loader can find the bytecode object.

---

## 3. Filter Transpilation

When you initialize `new Divert(filter_string)` on Linux, JDivert's `FilterTranspiler` parses the WinDivert-style filter expression and transpiles it into structured binary rules (`BpfFilterRule` or `BpfFilterRuleIpv6`):

- **Fields Parsed**: Protocol (`tcp`, `udp`, `icmp`), source/destination IP addresses, CIDR ranges, port ranges, loopback, direction (`inbound`/`outbound` matching ingress/egress), TTL, and TCP flags.
- **Map Updates**: The transpiled rules are loaded into the BPF array maps (`filter_rules` and `filter_rules_ipv6`) at index 0.

---

## 4. Layer & Flag Translations

Because eBPF is bound to the Traffic Control (TC) subsystem, certain WinDivert concepts are emulated:

| WinDivert Layer | Linux eBPF Implementation | Behavior |
| :--- | :--- | :--- |
| `Layer.NETWORK` | TC ingress & egress hooks | **Fully supported** (capture, drop, modify, inject). |
| `Layer.FLOW` | TC hooks + Event mapping | **Sniff-only**. Connection events are captured, but packets cannot be blocked. |
| `Layer.SOCKET` | TC hooks + Event mapping | **Sniff-only**. Socket-level metadata is emulated where possible. |
| `Layer.REFLECT` | N/A | **Not supported** on Linux. |

| WinDivert Flag | eBPF Implementation |
| :--- | :--- |
| `Flag.SNIFF` | BPF returns `TC_ACT_OK`, letting the packet continue while copying it to user-space. |
| `Flag.DROP` | BPF returns `TC_ACT_SHOT`, dropping the packet immediately. |
| `Flag.FRAGMENTS` | Supported. BPF logic handles L3 detection for fragmented packets. |
| `Flag.RECV_ONLY` | Captures packets but disables the raw socket used for re-injection. |
| `Flag.SEND_ONLY` | Disables TC hook attachment; used only for packet injection. |

---

## 5. Linux Permissions & Capabilities

Interacting with Traffic Control and loading BPF bytecode requires elevated privileges. Your Java application must run:
- As root (e.g., `sudo java -jar app.jar`).
- OR with the capabilities `CAP_NET_ADMIN` and `CAP_BPF` granted to the Java binary:
  ```bash
  sudo setcap cap_net_admin,cap_bpf+ep /path/to/your/java/bin/java
  ```
