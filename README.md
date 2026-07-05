# JDivert

[![Build and Test](https://github.com/ffalcinelli/jdivert/actions/workflows/ci.yml/badge.svg)](https://github.com/ffalcinelli/jdivert/actions/workflows/ci.yml)
[![Coverage Status](https://img.shields.io/codecov/c/github/ffalcinelli/jdivert/main.svg)](https://codecov.io/github/ffalcinelli/jdivert)
[![Maven Central Repo](https://img.shields.io/maven-central/v/com.github.ffalcinelli/jdivert.svg)](https://search.maven.org/artifact/com.github.ffalcinelli/jdivert/3.0.0/jar)
[![license](https://img.shields.io/badge/license-LGPLv3%20%7C%20GPLv2-blue.svg)](https://github.com/ffalcinelli/jdivert/blob/master/LICENSE)

**JDivert** is a powerful Java binding for capturing, modifying, and dropping network packets. It supports **Windows** via [WinDivert](https://reqrypt.org/windivert.html) and **Linux** via **eBPF**.

> [!WARNING]
> Linux support via eBPF is experimental and should not be used in production environments.

---

## Quick Start

Capture and re-inject all TCP traffic on port 80:

```java
try (WinDivert w = new WinDivert("tcp.DstPort == 80").open()) {
    while (true) {
        Packet packet = w.recv();
        packet.getTcp().ifPresent(tcp -> {
            System.out.println("Captured TCP packet to port: " + tcp.getDstPort());
        });
        w.send(packet); 
    }
}
```

For more complex scenarios, see our [Examples Guide](file:///home/fabio/Workspace/divert/jdivert/docs/examples.md).

---

## Usage Hints

### Prerequisites
*   **Operating System**: Windows (64-bit) or Linux (64-bit, kernel 5.8+).
*   **Privileges**: Administrator privileges are **required** on Windows to load the WinDivert driver, and root privileges (or `CAP_NET_ADMIN` and `CAP_BPF` capabilities) are **required** on Linux.
*   **Java**: Version 8 or higher (Version 22 or higher required for Panama-based eBPF/WinDivert adapters).

### Basic Patterns
1.  **Always use try-with-resources**: JDivert manages native handles and buffers. The `WinDivert` and `WinDivertAsyncResult` classes implement `AutoCloseable` to ensure these resources are released.
2.  **Filter specifically**: Use the [Filter Language](file:///home/fabio/Workspace/divert/jdivert/docs/filters.md) to capture only the traffic you need. This happens in kernel-mode and is significantly faster than filtering in Java.
3.  **Handle Re-injection**: When you receive a packet with `recv()`, it is removed from the network stack. If you don't call `send()`, the packet is dropped.

---

## Building and Testing

### Prerequisites
*   Maven 3.9+
*   Vagrant & VirtualBox (Required for full test execution)

### Running Tests
Due to the nature of the WinDivert driver and its requirement for specific network stack interactions and Administrator privileges, the full test suite is designed to be executed within a clean, isolated Windows 11 environment managed by **Vagrant**.

1.  **Boot the VM**:
    ```bash
    vagrant up
    ```
2.  **Sync and Execute**:
    Run the following commands to sync the latest code to a local directory in the VM (avoiding synced folder permission issues) and execute the tests:
    ```bash
    vagrant winrm --command "robocopy C:\jdivert C:\local_jdivert /MIR /XD .git .vagrant target"
    vagrant winrm --command "cd C:\local_jdivert; mvn clean verify"
    ```

*Note: While you can compile the project on any OS, actual packet capture tests will only succeed in the provided Vagrant environment or an elevated Windows session.*

---

## Architecture

JDivert bridges the gap between Java and the native WinDivert C library using **JNA** (with optional **Project Panama** support on Java 22+). 

*   **Zero-Copy**: Leverages direct buffers to process packets without redundant memory copying.
*   **Memory-Safe**: Employs deterministic cleanup to prevent native memory leaks.
*   **Zero-Install**: WinDivert binaries are bundled and extracted automatically into versioned temporary directories.

Read the full [Architecture Overview](file:///home/fabio/Workspace/divert/jdivert/docs/architecture.md) for more details.

---

## Installation

### Maven
```xml
<dependency>
  <groupId>com.github.ffalcinelli</groupId>
  <artifactId>jdivert</artifactId>
  <version>3.0.0</version>
</dependency>
```

### Gradle
```groovy
implementation 'com.github.ffalcinelli:jdivert:3.0.0'
```

---

## Credits

JDivert is a Java wrapper around the excellent **WinDivert** project created by **basil00**.
We would like to thank the WinDivert community for providing such a powerful tool for network manipulation on Windows.

*   [WinDivert Official Website](https://reqrypt.org/windivert.html)
*   [WinDivert GitHub Repository](https://github.com/basil00/WinDivert)

---

## Documentation

*   [Full API Reference (Javadoc)](https://ffalcinelli.github.io/jdivert/api/apidocs/)
*   [Architecture Overview](file:///home/fabio/Workspace/divert/jdivert/docs/architecture.md)
*   [Linux eBPF Backend Guide](file:///home/fabio/Workspace/divert/jdivert/docs/linux_backend.md)
*   [Filter Language Guide](file:///home/fabio/Workspace/divert/jdivert/docs/filters.md)
*   [Examples Guide](file:///home/fabio/Workspace/divert/jdivert/docs/examples.md)
*   [Performance Considerations](file:///home/fabio/Workspace/divert/jdivert/docs/performance.md)
*   [Troubleshooting Guide](file:///home/fabio/Workspace/divert/jdivert/docs/troubleshooting.md)
*   [Security Policy](file:///home/fabio/Workspace/divert/jdivert/SECURITY.md)

---

## License

JDivert is dual-licensed under **LGPL-3.0-or-later** and **GPL-2.0-or-later**.
Refer to the `LICENSE`, `LICENSE-LGPL-3.0-or-later`, and `LICENSE-GPL-2.0-or-later` files for the full license text.
