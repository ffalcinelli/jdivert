# JDivert

JDivert is a Java binding for capturing, modifying, dropping and injecting network packets. Its native backends
are WinDivert on Windows and libebpfdivert (eBPFDivert) on Linux. Both expose the same WinDivert API, so
everything above the native adapter is platform-neutral, and the same filters, layers and flags work on both.

## Commands

The build requires **JDK 25**; the library itself targets Java 8. CI builds with 25, then runs the tests on
JDK 8, 11, 17, 21 and 25 via `-Dsurefire.jvm=...`, on both Windows and Linux.

```bash
mvn clean verify                              # build, test, JaCoCo
mvn -Dtest=PacketTestCase test                # single test class
mvn -Dtest=PacketTestCase#testMethod test     # single test method
mvn javadoc:javadoc                           # output goes to docs/api (published to GitHub Pages)
mvn clean verify -Debpfdivert.skipDownload=true \
    -Debpfdivert.local=../ebpfdivert/libebpfdivert.so   # test against a local eBPFDivert build
                                              # (add -Debpfdivert.local.platform=linux-aarch64 on arm64)
```

Live capture tests need root on Linux or Administrator on Windows. Run them in the Vagrant VMs:

```bash
vagrant up linux && vagrant ssh linux -c "cd /jdivert && sudo mvn clean verify"; vagrant destroy -f linux
vagrant up windows
vagrant winrm windows --command "robocopy C:\jdivert C:\local_jdivert /MIR /XD .git .vagrant target"
vagrant winrm windows --command "cd C:\local_jdivert; mvn clean verify"
```

The robocopy step avoids synced-folder permission problems on Windows. `run_tests.bat` is stale: it still
calls Gradle.

## Architecture

More detail is in `docs/architecture.md` and `docs/linux_backend.md`.

- **Public API** (`com.github.ffalcinelli.jdivert`): `WinDivert`/`Divert` (handles), `WinDivertAsyncResult`,
  `Packet`, `headers/` (IPv4/IPv6/TCP/UDP/ICMP parsing and checksums), and `Enums`. Handles are `AutoCloseable`.
- **Native adapters:**
  - `windivert.NativeAdapter` is the backend contract. `windivert.NativeAdapterFactory` picks the adapter by OS
    and runtime.
  - On Java 22+ it uses the `{WinDivert,EBPFDivert}PanamaNativeAdapter` classes in `src/main/java22`, packaged
    as a multi-release jar under `META-INF/versions/22`.
  - Otherwise it uses the JNA adapters in `windivert/` and `ebpfdivert/`. `ebpfdivert/jna/LibEbpfDivert` is the
    JNA interface.
  - Async calls run the blocking native call on a virtual thread on Java 21+, otherwise on a daemon pool.
- **Address layout.** `windivert.AddressCodec` converts `WinDivertAddress` to and from the native 80-byte
  layout. It must preserve the whole 64-byte union, because libebpfdivert stores the capture context there for
  re-injection.
- **Native binaries:**
  - **Download.** They are not in git. During `generate-resources`, Maven downloads the releases pinned by the
    `windivert.version` and `ebpfdivert.version` properties in `pom.xml` (eBPFDivert for amd64 and arm64) and
    bundles them into the jar.
  - **Extraction.** At runtime, `windivert.DeployHandler` extracts them to a per-user, versioned directory:
    `${java.io.tmpdir}/jdivert-<version>-<user>/`, mode 0700 on Linux.
- **Linux-only system properties:** `jdivert.ebpf.interfaces` and `jdivert.ebpf.ringBytes`.

## Rules

- `src/main/java` must stay Java 8 compatible. Panama code belongs only in `src/main/java22`.
- Keep `jna.library.path` handling in `DeployHandler` non-invasive. Set it only around `Native.load`, then
  restore any previous value, so other JNA users in the same JVM are not affected.
- Any change to the adapter contract must be implemented in all four adapters.
- Tests use JUnit 5 and are named `*TestCase`. Tests that need a live backend (for example
  `LiveCaptureTestCase`, `EventLayersTestCase`) are gated with `CaptureCondition` (Windows, or root on Linux),
  so they skip when unprivileged. New live tests must do the same.
- On Linux, `WinDivertException` carries errno values, not Windows error codes: `EINVAL` 22 for a bad filter
  (87 on Windows), `EPERM` 1, `EOPNOTSUPP` 95. Tests must not hard-code the Windows codes.
- To bump a native version, change the `pom.xml` property, then run the full suite on both platforms.
- The license is LGPL-3.0-or-later OR GPL-2.0-or-later. Give new files the same license header as their
  neighbors.
- GitHub Actions must be pinned to full commit SHAs with a `# vX.Y.Z` comment, verified by `ffalcinelli/pinner`
  (`.pinner.toml`).
