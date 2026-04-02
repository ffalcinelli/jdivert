# JDivert - Java Bindings for WinDivert

JDivert is a Java wrapper for [WinDivert](https://reqrypt.org/windivert.html), a Windows driver that allows user-mode applications to capture, modify, and drop network packets sent to or from the Windows network stack.

## Project Overview

- **Purpose:** Provide a high-level, idiomatic Java API for interacting with the WinDivert driver.
- **Main Technologies:**
  - **Java:** Source compatibility set to Java 1.8.
  - **JNA (Java Native Access):** Used to interface with the native WinDivert DLL.
  - **Gradle:** Build system and dependency management.
  - **WinDivert:** Bundles WinDivert 2.2.2 binaries (`.dll` and `.sys`) for both x86 and x64 architectures.
  - **GitHub Actions:** CI/CD platform for automated building and testing on Windows across multiple JDK versions (8, 11, 17, 21).
- **Architecture:**
  - `com.github.ffalcinelli.jdivert.WinDivert`: The primary entry point for opening a capture handle using a filter string.
  - `com.github.ffalcinelli.jdivert.Packet`: Represents a network packet, providing access to headers (IP, TCP, UDP, ICMP) and payload.
  - `com.github.ffalcinelli.jdivert.windivert.DeployHandler`: A critical component that extracts the bundled native binaries to a temporary directory at runtime and configures JNA to load them.
  - `com.github.ffalcinelli.jdivert.headers`: A package containing classes for parsing and manipulating various network protocol headers.

## Building and Running

The project uses the Gradle wrapper (`gradlew`) for all build tasks.

- **Build the project:**
  ```bash
  ./gradlew build
  ```
- **Run tests:**
  ```bash
  ./gradlew test
  ```
  *Note: Many tests require Windows and Administrator privileges to interact with the WinDivert driver.*
- **Clean the build directory:**
  ```bash
  ./gradlew clean
  ```
- **Generate Javadoc:**
  ```bash
  ./gradlew javadoc
  ```
  The project has a custom task `copyJavadoc` that moves generated Javadocs to the `docs/` directory for GitHub Pages.

## Development Conventions

- **Licensing:** The project is licensed under the GNU Lesser General Public License 3 (LGPL-3.0). All source files should include the standard license header.
- **Native Binaries:** WinDivert `.dll` and `.sys` files are located in `src/main/resources`. Do not modify these unless updating the bundled WinDivert version.
- **Coding Style:** Adheres to standard Java coding conventions.
- **Testing:**
  - JUnit 4 is used for unit and integration testing.
  - Tests are located in `src/test/java`.
  - Some tests (e.g., `LiveCaptureTestCase`) perform actual network capture and require specific environment setups.
- **Dependencies:**
  - JNA is the core dependency for native interop.
  - Keep the `jna.library.path` management in `DeployHandler` robust to avoid interfering with other JNA usages in the same JVM.
