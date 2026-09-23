# Linux Backend Guide

On Linux, JDivert is backed by [eBPFDivert](https://github.com/ffalcinelli/ebpfdivert): `libebpfdivert.so`, a C
library that implements the WinDivert API with eBPF. The same `WinDivert` class, filters, layers, flags,
parameters and packet metadata work unchanged on both operating systems.

---

## 1. Requirements

- Linux **5.10 or newer** with BTF (`/sys/kernel/btf/vmlinux`), which is the default on current distributions.
  It is tested on 5.15 and 6.8.
- **cgroup v2**, for the FLOW and SOCKET layers.
- x86_64 or aarch64, glibc 2.28+.
- Root, or the capabilities `CAP_BPF`, `CAP_NET_ADMIN` and `CAP_NET_RAW` on the JVM:
  ```bash
  sudo setcap cap_bpf,cap_net_admin,cap_net_raw+ep /path/to/java
  ```

Nothing else needs to be installed. The jar bundles a self-contained `libebpfdivert.so` for each architecture
(`linux-x86-64/`, `linux-aarch64/`), with the BPF programs and libbpf built in. At runtime, `DeployHandler`
extracts it to a private per-user directory, `${java.io.tmpdir}/jdivert-<version>-<user>/` (mode 0700).

---

## 2. Native adapters

`NativeAdapterFactory` picks the adapter:

- **`EBPFDivertPanamaNativeAdapter`** on Java 22+: Foreign Function & Memory downcalls.
- **`EBPFDivertJnaNativeAdapter`** on Java 8–21: JNA, through the `LibEbpfDivert` interface.

Both adapters are thin 1:1 mappings of `NativeAdapter` onto the library. Addresses cross the boundary in the
native 80-byte `WINDIVERT_ADDRESS` layout (`AddressCodec`). The whole 64-byte union is preserved, so a packet
received and sent back carries the capture context the library needs to re-inject it where it was taken.

The async calls (`recvAsync`, `sendAsync`) run the blocking native call on a virtual thread (Java 21+) or on a
cached daemon pool.

---

## 3. How it works

- **Filters.** They are compiled by WinDivert's own filter compiler, which is built into the library, then
  lowered to eBPF rules running on TC ingress/egress.
  - If a filter cannot be expressed exactly in the kernel (for example `tcp.PayloadLength > 100`), the kernel
    captures a superset. The library then evaluates the exact filter and re-injects the packets that don't
    match.
  - `recv()` therefore returns exactly what WinDivert would return.
- **Loopback.** Loopback traffic is reported once, as outbound with `isLoopback()`.
- **Priorities.** Handles chain by priority. Re-injected packets are seen only by lower-priority handles, as
  impostors.
- **Large packets.** GSO/GRO packets of up to 64 KB are captured and re-injected whole, and offloads can stay
  on. Use a receive buffer of at least 65575 bytes (the default).
- **Crashes.** If the JVM is killed, its kernel programs stop diverting within 3 seconds. They are removed when
  the next handle opens.

---

## 4. Layers and flags

| Layer | Linux implementation | Notes |
| :--- | :--- | :--- |
| `NETWORK` | TC hooks | Full support: capture, modify, drop, inject. |
| `NETWORK_FORWARD` | TC hooks | Routed packets only. |
| `FLOW` | cgroup/sockops programs | TCP and UDP flows; `SNIFF \| RECV_ONLY` is required, as on Windows. |
| `SOCKET` | cgroup programs | BIND, CONNECT, LISTEN, ACCEPT, CLOSE with the process ID; `RECV_ONLY` is required. |
| `REFLECT` | handle registry | Divert handles of all processes; `SNIFF \| RECV_ONLY` is required. |

`SNIFF`, `DROP`, `FRAGMENTS`, `RECV_ONLY` and `SEND_ONLY` behave as on Windows, and `NO_INSTALL` is accepted.
The `QUEUE_LEN`, `QUEUE_TIME` and `QUEUE_SIZE` parameters and `shutdown` are supported.

### Differences from Windows

- **SOCKET blocking.** Without `SNIFF`, a SOCKET handle blocks the matching BIND and CONNECT calls, and the
  process gets `EPERM`. Linux cannot block LISTEN and ACCEPT. A filter that could match them, or that uses
  fields other than event, protocol, local/remote address and port, and `processId`, fails to open unless
  `SNIFF` is set.
- **Error codes.** `WinDivertException` carries Linux errno values:
  - 22 (`EINVAL`) for a bad filter (87 on Windows).
  - 1 (`EPERM`) for missing privileges.
  - 95 (`EOPNOTSUPP`) for unsupported combinations.
- **Timestamps** are `CLOCK_MONOTONIC` nanoseconds.

---

## 5. Linux-only system properties

| Property | Default | Meaning |
| --- | --- | --- |
| `jdivert.ebpf.interfaces` | all | Comma-separated interfaces to capture on (loopback is always included). |
| `jdivert.ebpf.ringBytes` | 8 MiB | Kernel ring buffer size per handle. |

---

## 6. Building against a local eBPFDivert

The build downloads the release pinned by `ebpfdivert.version` in `pom.xml`. To use a local build instead:

```bash
mvn clean verify -Debpfdivert.skipDownload=true \
    -Debpfdivert.local=/path/to/libebpfdivert.so     # -Debpfdivert.local.platform=linux-aarch64 on arm64
```

Tests that capture traffic need root. Run them in the Vagrant `linux` machine, then destroy it:

```bash
vagrant up linux && vagrant ssh linux -c "cd /jdivert && sudo mvn clean verify"
vagrant destroy -f linux
```
