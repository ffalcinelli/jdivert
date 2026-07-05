# Troubleshooting Guide

This guide covers common issues encountered when using JDivert on both Windows and Linux, along with solutions to resolve them.

---

## Windows-Specific Issues

### 1. `WinDivertException: Access is denied` (Error 5)
- **Cause**: WinDivert requires **Administrator privileges** to load its driver and interact with the network stack.
- **Solution**:
  - Run your IDE (IntelliJ, Eclipse, VS Code) as Administrator.
  - If running from the command line, use an elevated Command Prompt or PowerShell window.
  - If using Vagrant, ensure the virtualization provider is running with sufficient privileges.

### 2. `WinDivertException: The system cannot find the path specified` (Error 3)
- **Cause**: JDivert failed to extract the native `.dll` or `.sys` binaries to the temporary directory, or they were blocked/quarantined by security software.
- **Solution**:
  - Check if your antivirus software is blocking the extraction or loading of `WinDivert64.sys`.
  - Ensure the user account running Java has write permissions to the temporary directory (usually `%TEMP%`).
  - Specify a custom temporary directory by setting the `java.io.tmpdir` system property: `java -Djava.io.tmpdir=C:\my_tmp -jar app.jar`.

### 3. `WinDivertException: The parameter is incorrect` (Error 87)
- **Cause**: This indicates a syntax error in your **WinDivert filter string**.
- **Solution**:
  - Validate your filter syntax against the [Filter Language Guide](file:///home/fabio/Workspace/divert/jdivert/docs/filters.md).
  - Check that all field names are typed correctly (e.g. use `ip.SrcAddr` instead of `ip.src`).
  - Test the driver with a simple filter like `"true"` to isolate the issue.

### 4. `WinDivertException: The driver is blocked from loading` (Error 1275)
- **Cause**: Driver Signature Enforcement or security policies are preventing the WinDivert kernel driver from loading.
- **Solution**:
  - Ensure you are using the latest version of JDivert, which bundles properly signed WinDivert driver binaries.
  - Check Windows Defender Application Control policies.

---

## Linux-Specific Issues (eBPF Backend)

### 1. `WinDivertException` or `IOException: Permission denied` / `Operation not permitted`
- **Cause**: Interacting with Traffic Control (TC) and loading eBPF bytecode requires elevated root privileges or specific capabilities.
- **Solution**:
  - Run your Java application as root (e.g., `sudo java -jar app.jar`).
  - Alternatively, grant the JVM binary the required network capabilities:
    ```bash
    sudo setcap cap_net_admin,cap_bpf+ep /path/to/your/java/bin/java
    ```

### 2. `RuntimeException: Failed to open eBPF handle (pcap_ringbuf map not found)`
- **Cause**: The kernel-side eBPF program failed to load or map pinning in `/sys/fs/bpf/ebpfdivert/` failed.
- **Solution**:
  - Ensure your kernel version is 5.8+ and BTF is enabled (`CONFIG_DEBUG_INFO_BTF=y`).
  - Check the output logs or system kernel log (`dmesg`) for eBPF verifier errors.
  - Ensure the `/sys/fs/bpf` filesystem is mounted:
    ```bash
    mount -t bpf bpffs /sys/fs/bpf
    ```

### 3. JNA/Panama Linkage Errors: `libbpf.so not found`
- **Cause**: The system is missing the dynamic BPF library needed for loading BPF objects.
- **Solution**:
  - Install the developer packages for `libbpf`:
    ```bash
    # Ubuntu/Debian
    sudo apt-get install libbpf-dev
    # Fedora/CentOS
    sudo dnf install libbpf-devel
    ```

---

## Platform-Neutral Issues

### 1. Packet capture works, but network connectivity is lost
- **Cause**: In `recv()` mode, JDivert **takes packets out** of the network stack. If they are not re-injected using `send(packet)`, they are dropped.
- **Solution**:
  - Ensure your capture loop always calls `w.send(packet)` unless you explicitly want to drop/block the packet.
  - Wrap packet processing in `try-finally` blocks to guarantee that re-injection occurs even if an exception is thrown.

### 2. `NoClassDefFoundError: com/sun/jna/Library`
- **Cause**: The JNA dependency is missing from the runtime classpath.
- **Solution**:
  - If using Maven or Gradle, verify that the JNA dependency is declared and not set to `provided`.
  - If running a standalone JAR, build it as a shaded/fat JAR or supply JNA via the `-cp` classpath flag.
