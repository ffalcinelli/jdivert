# JDivert

[![Build and Test](https://github.com/ffalcinelli/jdivert/actions/workflows/ci.yml/badge.svg)](https://github.com/ffalcinelli/jdivert/actions/workflows/ci.yml)
[![Coverage Status](https://img.shields.io/codecov/c/github/ffalcinelli/jdivert/main.svg)](https://codecov.io/github/ffalcinelli/jdivert)
[![Maven Central Repo](https://img.shields.io/maven-central/v/com.github.ffalcinelli/jdivert.svg)](https://search.maven.org/artifact/com.github.ffalcinelli/jdivert/3.0.0/jar)
[![license](https://img.shields.io/badge/license-LGPLv3%20%7C%20GPLv2-blue.svg)](https://github.com/ffalcinelli/jdivert/blob/master/LICENSE)

**JDivert** is a powerful Java binding for [WinDivert](https://reqrypt.org/windivert.html), a Windows driver that allows user-mode applications to capture, modify, and drop network packets sent to or from the Windows network stack.

---

## Quick Start

Capture and re-inject all TCP traffic on port 80:

```java
try (WinDivert w = new WinDivert("tcp.DstPort == 80").open()) {
    while (true) {
        Packet packet = w.recv();
        System.out.println("Captured: " + packet);
        w.send(packet); 
    }
}
```

For more complex scenarios, see our [Examples Guide](docs/examples.md).

---

## Usage Hints

### Prerequisites
*   **Operating System**: Windows (64-bit).
*   **Privileges**: Administrator privileges are **required** to load the WinDivert driver and open capture handles.
*   **Java**: Version 8 or higher.

### Basic Patterns
1.  **Always use try-with-resources**: JDivert manages native handles and buffers. The `WinDivert` and `WinDivertAsyncResult` classes implement `AutoCloseable` to ensure these resources are released.
2.  **Filter specifically**: Use the [Filter Language](docs/filters.md) to capture only the traffic you need. This happens in kernel-mode and is significantly faster than filtering in Java.
3.  **Handle Re-injection**: When you receive a packet with `recv()`, it is removed from the network stack. If you don't call `send()`, the packet is dropped.

---

## Building and Testing

### Prerequisites
*   Maven 3.9+
*   Windows 10/11 with Administrator access.

### Running Tests
To run the full test suite, execute:
```bash
mvn clean test
```
*Note: Many integration tests will be skipped or fail if not run on Windows with elevated privileges.*

### Local Development with Vagrant
If you are developing on a non-Windows machine, a `Vagrantfile` is provided to spin up a Windows 11 environment:
1.  Run `vagrant up` to boot the VM.
2.  Follow the instructions in the `Vagrantfile` output to sync your code and run tests inside the VM.

---

## Architecture

JDivert bridges the gap between Java and the native WinDivert C library using **JNA** (with optional **Project Panama** support on Java 22+). 

*   **Zero-Copy**: Leverages direct buffers to process packets without redundant memory copying.
*   **Memory-Safe**: Employs deterministic cleanup to prevent native memory leaks.
*   **Zero-Install**: WinDivert binaries are bundled and extracted automatically into versioned temporary directories.

Read the full [Architecture Overview](docs/architecture.md) for more details.

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
*   [Architecture Overview](docs/architecture.md)
*   [Filter Language Guide](docs/filters.md)
*   [Examples Guide](docs/examples.md)
*   [Performance Considerations](docs/performance.md)
*   [Troubleshooting Guide](docs/troubleshooting.md)
*   [Security Policy](SECURITY.md)

---

## License

JDivert is dual-licensed under **LGPL-3.0-or-later** and **GPL-2.0-or-later**.
Refer to the `LICENSE`, `LICENSE-LGPL-3.0-or-later`, and `LICENSE-GPL-2.0-or-later` files for the full license text.
