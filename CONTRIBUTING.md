# Contributing to Light Touch

Light Touch runs a library of emulated legacy iOS devices (iPhone, iPhone 3GS, iPhone 4, iPod touch 1G to 4G
and iPad) on Android, Linux, Windows and macOS. Start with [README.md](README.md), the asset guide
[docs/ASSETS.md](docs/ASSETS.md) and [Desktop/README.md](Desktop/README.md). The upstream macOS Cocoa/Xcode app
this fork started from has been removed; the macOS port is the shared desktop UI built with SwiftPM.

## Layout

| Path | What |
|---|---|
| `Portable/` | The shared C/C++ base: runtime, storage, library, guest package and screenshot code, plus `firmware/` (the shared `firmware-catalog.json`; the preparer is `Packages/FirmwareKit`) |
| `Desktop/` | The shared Tk/SDL desktop UI (`lighttouch_desktop`, `ui`), the SDL window (`native/`), app services, and the `build.py` dispatcher over `targets.json` |
| `Linux/`, `Windows/`, `macOS/`, `Android/` | One directory per port: its host, packaging recipes and port-specific tests |
| `Package.swift` | The macOS SwiftPM build: `LightTouchDesktopHost` (`macOS/SharedHost`), `LightTouchSharedDesktopMac` (`macOS/SharedDesktop`) and `LightTouchDevice`, the per-device engine helper |
| `LightTouchDevice/` | The macOS per-device helper: one QEMU instance, frames over IOSurface, control over the `DeviceRuntime` link, the device's web proxy |
| `Packages/HostRuntime/` | Boards, boot recipes, device lock, storage types shared by the Swift code |
| `Packages/DeviceRuntime/` | The host-helper link (`DeviceLink`, `DeviceRendezvous`, the `LTMLinkC` module) |
| `crates/` | The Rust `firmwarekit` taken from LightTouch (`lighttouch-firmware`), which carries the iPhone 5's N42 recipe, its A6 restore and first boot; root `Cargo.toml`/`Cargo.lock` are its workspace |
| `Packages/FirmwareKit/` | `FirmwareKit` (IPSW to device), `FirmwareSchema`, the `firmwarekit` CLI and `CActivation` (the activation recognizer; `Portable/firmware` compiles the same source) |
| `Packages/LightTouchCore/` | The Swift domain logic `LightTouchDesktopHost` links (library, session, catalog, guest, input) |
| `Packages/DeviceServices/` | `HostServiceWire` and `HostServiceClient`, which `LightTouchCore` uses to talk to a device's stock lockdown services |
| `build-support/` | `dependencies.json` (pinned archives), `sources.json` (the qemu-ios and usbmuxd pins) and build patches |
| `Configuration/` | The `LightTouchDevice` Info.plist and entitlements |
| `tools/` | Firmware matrix and preparation tools, `get-assets.py`, the activation tool, `perf/` (benchmark harness), developer-access tools and engine research scripts |
| `tests/` | `activation/` (run script), `fixtures/` (store fixtures, the fake preparer, `machines.json` shared by tests), `port/` (Android package check tests) |

## Firmwares in the catalog

The shared [firmware catalog](Portable/firmware/firmware-catalog.json) is the source of truth for builds,
availability, keys and preparation recipes. `firmwarekit`, the preparer every port uses (see [Portable/firmware/README.md](Portable/firmware/README.md)),
reads it:

```sh
firmwarekit create --catalog Portable/firmware/firmware-catalog.json \
  --id k48ap-7B500 --ipsw /path/to/stock.ipsw --out /path/to/new-device \
  --helper /path/to/LightTouchDevice --guest-tools /path/to/ipad-guest-tools
```

Catalog availability is distinct from measured acceptance. The iPhone 5's `n42ap-14G61` entry is the one row
prepared by the Rust `firmwarekit` (`crates/`); everything else goes through `Packages/FirmwareKit`.

## Repositories

| Repo | What it is |
|---|---|
| This repository | The frontends, the shared base, the preparers and the packaging recipes |
| [qemu-ios](https://github.com/samhenrigold/qemu-ios) | The emulator (`hw/arm/ipod_touch_*.c`, `hw/arm/ipad1.c`, `hw/arm/s5l8930_*.c`) and the guest tools under `contrib/`; the engine each port builds against |
| [usbmuxd](https://github.com/samhenrigold/usbmuxd) (fork) | The usbmuxd that bridges the emulated USB device to libimobiledevice; commit pinned in `build-support/sources.json` |

## Building

For development, `scripts/build.sh` builds the engine, the Linux frontend, the Android APK, and (on a
Mac) the guest package and LT Bench, with outputs saved in `build/`: see [docs/BUILDING.md](docs/BUILDING.md).
The per-platform routes below are the full recipes.

Each port has its own recipe; none needs Xcode.

- **Linux**: `cmake -S Linux -B BUILD -DLIGHTTOUCH_QEMU_SOURCE=/path/to/qemu-ios && cmake --build BUILD -j2`; AppImage packaging is in `Linux/packaging/README.md`.
- **Windows**: `Windows/README.md`.
- **macOS**: `macOS/README.md` (engine) and `macOS/SharedDesktop/README.md` (`macOS/build-shared.py`, Swift 6.2 and SDL2).
- **Android**: `Android/BUILD-AND-RUN.md` (`cd Android && ./gradlew assembleDebug` with the properties it lists).
- **All desktop targets**: `python3 Desktop/build.py --help` and `Desktop/README.md`.

## Tests

No runner wraps these; run what covers the code you changed.

```sh
python3 -m unittest discover -s Desktop/tests          # shared UI/library/packaging contracts (some need tkinter)
python3 -m unittest discover -s Linux/tests -p 'test_*.py'
python3 -m unittest discover -s tools/tests
python3 -m unittest discover -s tests/port
swift test --package-path Packages/HostRuntime         # likewise DeviceRuntime, FirmwareKit, LightTouchCore, DeviceServices (macOS)
sh tests/activation/run.sh                             # activation recognizer
```

`swift test --package-path Packages/FirmwareKit` also runs FirmwareKit's suites alone. Optional corpus tests report
real skips unless `FK_TEST_CORPUS=1` or `FK_REQUIRE_FIXTURES=1` selects them; selected missing inputs fail.
`Portable/tests` and `Linux/tests/*.cpp` are CMake test targets (`-DLIGHTTOUCH_BUILD_TESTS=ON`). Every headless
boot passes `-audio driver=none`.

## Style

Apple's [API Design Guidelines](https://www.swift.org/documentation/api-design-guidelines/) first; where they are
silent, [Google's Swift style guide](https://google.github.io/swift/). American spelling, except Apple's cancelled,
cancelling and cancellable.

**Format.** Swift uses 4 spaces and 120 columns, one argument per line once a call wraps, no blank line after `{` or before `}`,
implicit returns, `[]`/`[:]` for empty collections, and `for … where` over an `if` wrapping a loop body;
`clang-format` (`.clang-format`) owns C.

**Warnings.** None. Fix the cause: a `@Sendable` closure or a lock-guarded `@unchecked Sendable` box for state shared
across threads, `nonisolated` on test fixtures in the MainActor-by-default packages. A deprecated API we must keep goes in one small C file with a scoped
`#pragma clang diagnostic` and a comment saying why (`LightTouchDevice/GuestTLS.c`).

**Files.** One main type per file, named after it; helpers only it uses can live beside it. No created-by or license
headers. A file whose role isn't obvious from its name opens with a short `//` paragraph saying what it is and why,
then the imports (sorted, one per line). `// MARK: - Topic` separates the sections of a long type; short files have
none.

**Access and types.** `private` by default, `private(set)` for state others read; never spell out `internal`;
`public` only for what another module uses. Access levels go on members, not on `extension`. Classes are `final`
unless designed for subclassing; prefer structs and enums; `actor` or a lock for shared mutable state. A conformance
that is its own topic (a delegate, a data source) goes in its own `extension`; `Sendable`, `Hashable` and the like
stay on the declaration.

**Naming.** Clear at the call site, no `get` prefixes, Booleans read as assertions (`isRunning`), factories start
with `make`. Acronyms keep one case: `deviceID`, `ipaURL`, `urlSession`. Wire and catalog names live in raw values
and `CodingKeys`, not in Swift identifiers, in new code.

**Comments.** `///` for documentation, never `/** */`; the first sentence summarizes; `- Parameter`/`- Returns` only
when they add something. Comments say why, not what. A trailing comment that won't fit in 120 columns goes above the
line.

**Code.** `guard` for early exits; `for`-`in` for a loop body (`.forEach` for one call); no `!`, `try!` or implicitly unwrapped optionals in new
non-test code unless the value can't be absent. `async`/`await` over completion handlers
in new code; `[weak self]` with `guard let self` in escaping closures, on the outermost closure that captures it.

**Tests.** Swift Testing (`@Test`, `#expect`); test names are camelCase sentences stating the behavior
(`aBootThatEndsBeforeUSBEndsItsStartup`).

## Boards

A board's facts have two homes. The emulator's: qemu-ios's `qemu_ios_device_info` table
(`contrib/ios-app/qemu-ios-ui.c`): the `-M` machine, the board ID, the screen, the modem, the USB host, the compass,
the USB charger and the `panel=` limits; the helper reports them in its hello and lists them with
`LightTouchDevice '{"mode":{"machines":{}}}'`, and the Swift packages read them as `DeviceInfo` (`Board.hardware`). The host's: one `Board`
case and its `Facts` in `Packages/HostRuntime/Sources/HostRuntime/Board.swift`: the names, the model ID, the SoC
family (which decides how its prepared base boots and its guest architecture) and the art. Adding a board touches
those two places; its firmware is catalog entries, and preparing it is a FirmwareKit recipe if no existing one fits.
`tests/fixtures/machines.json` is the listing the tests use without an emulator.

`device.lock.json` is `DeviceLock` (HostRuntime): FirmwareKit writes it, everything else reads it through that type.

## Rules

- **No Python bridge in the preparer.** `firmwarekit` is native; qemu-ios's Python `imgtools/` is the test oracle, not a runtime.
- **Activation is maintained separately.** FirmwareKit runs it as a built-in preparation step
  (`Packages/FirmwareKit/Sources/CActivation`, `tools/activation/`). Treat it as a black box.
- **No firmware in any repo.** IPSWs, decrypted components, NAND images, SDKs and prepared devices stay outside the repositories.
- **Engine changes.** A qemu-ios change a port needs lands there first, then the pin (`build-support/sources.json`) moves in the same change.
