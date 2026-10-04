# JDivert architecture

JDivert exposes one Java API, `WinDivert`/`Divert`, `Packet` and the headers, over two native backends that share
the same model: WinDivert on Windows, and eBPFDivert on Linux, which implements the WinDivert API with eBPF.
Because both libraries use the same layers, flags, parameters, filter language and 80-byte `WINDIVERT_ADDRESS`,
everything above the native adapter is platform-neutral.

```
 WinDivert / Divert / Packet / headers        (src/main/java, Java 8)
            │
       NativeAdapter  ◀── NativeAdapterFactory (by OS; Panama on Java 22+, else JNA)
   ┌────────┴────────────────────────┬───────────────────────────────────────┐
 WinDivertJnaNativeAdapter      EBPFDivertJnaNativeAdapter
 WinDivertPanamaNativeAdapter   EBPFDivertPanamaNativeAdapter     (src/main/java22)
   │                                  │
 WinDivert64.dll + .sys          libebpfdivert.so (linux-x86-64 / linux-aarch64)
 (Windows kernel driver)         (TC + cgroup eBPF programs, embedded)
```

## Native adapters

- **`NativeAdapter`** is the backend contract. It covers open/close, recv/send (with batch and async variants),
  params, shutdown, and the checksum/hash/filter helpers.
- **`NativeAdapterFactory`** loads the Panama adapter when the `java22` classes are present and usable, and
  falls back to JNA otherwise.
- **Multi-release jar.** The jar keeps `src/main/java` at Java 8. The Panama adapters live under
  `META-INF/versions/22`.
- **`AddressCodec`** converts `WinDivertAddress` to and from the native little-endian layout. It keeps the
  64-byte union intact, because libebpfdivert stores the capture context there for re-injection.

## Native binaries

- **Bundling.** The build downloads the pinned releases in `generate-resources`:
  - WinDivert from basil00/WinDivert (`windivert.version`).
  - eBPFDivert for amd64 and arm64 from ffalcinelli/ebpfdivert (`ebpfdivert.version`).

  They are bundled into the jar.
- **Extraction.** At runtime, `DeployHandler` extracts the binaries for the current platform into a versioned,
  per-user private directory (`jdivert-<version>-<user>` under `java.io.tmpdir`, mode 0700 on Linux) and points
  JNA/Panama at it.

## Linux specifics

See [linux_backend.md](linux_backend.md). The kernel-side design (TC hooks, filter lowering, injection paths,
priorities, event layers, crash safety) is documented in the eBPFDivert repository
([architecture](https://github.com/ffalcinelli/ebpfdivert/blob/main/docs/architecture.md)).
