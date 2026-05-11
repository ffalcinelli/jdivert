# JDivert

[![Build and Test](https://github.com/ffalcinelli/jdivert/actions/workflows/ci.yml/badge.svg)](https://github.com/ffalcinelli/jdivert/actions/workflows/ci.yml)
[![Coverage Status](https://img.shields.io/codecov/c/github/ffalcinelli/jdivert/main.svg)](https://codecov.io/github/ffalcinelli/jdivert)
[![Maven Central Repo](https://img.shields.io/maven-central/v/com.github.ffalcinelli/jdivert.svg)](https://search.maven.org/artifact/com.github.ffalcinelli/jdivert/3.0.0/jar)
[![license](https://img.shields.io/badge/license-LGPLv3%20%7C%20GPLv2-blue.svg)](https://github.com/ffalcinelli/jdivert/blob/master/LICENSE)

**JDivert** is a powerful Java binding for [WinDivert](https://reqrypt.org/windivert.html), a Windows driver that allows user-mode applications to capture, modify, and drop network packets sent to or from the Windows network stack.

---

## 🚀 Quick Start

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

## 🏗️ Architecture

JDivert bridges the gap between Java and the native WinDivert C library using **JNA** (with **Project Panama** support on Java 22+). 

- **Zero-Copy**: Leverages direct buffers to process packets without redundant memory copying.
- **Memory-Safe**: Uses `try-with-resources` and deterministic cleanup to prevent native memory leaks.
- **Zero-Install**: WinDivert binaries are bundled and extracted automatically into versioned directories.
- **Idiomatic Java**: Provides a high-level, `AutoCloseable` API.

Read the full [Architecture Overview](docs/architecture.md) for more details.

---

## 🛠️ Installation

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

## ⚡ Performance & Advanced Usage

JDivert is designed for high-performance packet processing. Key features include:

- **Zero-Copy Path**: End-to-end processing using direct `ByteBuffer` objects.
- **Asynchronous I/O**: Non-blocking packet capture via `recvAsync()`.
- **Multithreading**: Safe concurrent capture and injection.
- **Multi-Layer Support**: Support for `NETWORK`, `FLOW`, and `SOCKET` layers.

Check out the [Performance Guide](docs/performance.md) for optimization tips.

---

## ❓ Troubleshooting

Common issues like `Access is denied` (missing Administrator privileges) are covered in our [Troubleshooting Guide](docs/troubleshooting.md).

---

## 📚 Documentation

- [Full API Reference (Javadoc)](https://ffalcinelli.github.io/jdivert/api/apidocs/)
- [Architecture Overview](docs/architecture.md)
- [Filter Language Guide](docs/filters.md)
- [Examples Guide](docs/examples.md)
- [Performance Considerations](docs/performance.md)
- [Troubleshooting Guide](docs/troubleshooting.md)
- [Security Policy](SECURITY.md)

## ⚖️ License

JDivert is dual-licensed under **LGPL-3.0-or-later** and **GPL-2.0-or-later**.
