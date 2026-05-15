# Troubleshooting Guide

This guide covers common issues encountered when using JDivert and how to resolve them.

## 1. `WinDivertException: Access is denied` (Error 5)

**Cause**: WinDivert requires **Administrator privileges** to interact with the network stack and load its driver.
**Solution**: 
- Run your IDE (IntelliJ, Eclipse, VS Code) as Administrator.
- If running from the command line, use an elevated Command Prompt or PowerShell window.
- If using `vagrant`, ensure the provider (e.g., VirtualBox) is running with sufficient permissions.

## 2. `WinDivertException: The system cannot find the path specified` (Error 3)

**Cause**: JDivert failed to extract the native `.dll` or `.sys` files to the temporary directory, or the extracted files were deleted/blocked.
**Solution**:
- Check if your antivirus is blocking the extraction of `WinDivert64.sys`.
- Ensure the application has write permissions to the system's temporary directory (usually `%TEMP%`).
- You can manually specify a temporary directory by setting the `java.io.tmpdir` system property.

## 3. `WinDivertException: The parameter is incorrect` (Error 87)

**Cause**: This usually indicates an error in your **WinDivert filter string**.
**Solution**:
- Validate your filter syntax against the [WinDivert Filter Documentation](https://reqrypt.org/windivert-doc.html#filter_language).
- Ensure all field names are correct (e.g., `ip.SrcAddr` instead of `ip.src`).
- Test with a simple filter like `true` to see if the error persists.

## 4. `WinDivertException: The driver is blocked from loading` (Error 1275)

**Cause**: Windows is preventing the WinDivert driver from loading, often due to Driver Signature Enforcement or security software.
**Solution**:
- Ensure you are using the latest version of JDivert (which bundles signed WinDivert binaries).
- Check Windows Security / App & Browser Control settings.

## 5. Packet capture works but internet/connectivity is broken

**Cause**: When you receive a packet with `recv()`, it is **removed** from the network stack. If you don't call `send()`, the packet is effectively dropped.
**Solution**:
- Ensure your loop always calls `w.send(packet)` unless you explicitly intend to drop the packet.
- Check for exceptions in your processing logic that might be skipping the `send()` call.

## 6. `NoClassDefFoundError: com/sun/jna/Library`

**Cause**: JNA is missing from your classpath.
**Solution**:
- If using Maven or Gradle, ensure the dependency is correctly declared.
- If running a standalone JAR, ensure JNA is included in the fat JAR or provided via `-cp`.

## 7. `WinDivertException: The handle is invalid` (Error 6)

**Cause**: You are trying to use a `WinDivert` instance that has already been closed or failed to open.
**Solution**:
- Ensure you call `w.open()` before `w.recv()`.
- Use try-with-resources to manage the lifecycle and avoid using the handle after it's closed.
