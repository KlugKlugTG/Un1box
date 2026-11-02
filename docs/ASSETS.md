# Get the iOS files

LightTouch needs three files for its main device, the **iPod touch 2G on iOS 3.1.3
(`n72ap-7E18`)**:

| File | What it is | LightTouch calls it |
| --- | --- | --- |
| `n72ap-7E18.itbase` | The prepared iOS system | **iOS system** |
| `armv6.itpack` | Guest tools that run inside iOS | **Guest tools** (the Android app has its own) |
| `bootrom.bin` | The n72 SecureROM boot file | **Boot file** |

The upstream [LightTouchMac](https://github.com/samhenrigold/LightTouchMac/releases) app
release contains all three, and the routes below take them from it. Pick the first one
that suits you.

## 1. Easiest: let the script fetch them (Linux, Windows, macOS)

[`tools/get-assets.py`](../tools/get-assets.py) downloads the upstream app ZIP
(about 390 MB), checks its SHA-256 against the release it was tested with, and
unpacks the three files. It needs Python 3.8 or newer and nothing else; it does
not need a Mac.

From a checkout of this repository:

```sh
python3 tools/get-assets.py            # Windows: py tools\get-assets.py
```

Or without a checkout:

```sh
curl -LO https://raw.githubusercontent.com/blake-lucas/LightTouch/main/tools/get-assets.py
python3 get-assets.py
```

The files land in `LightTouch-assets/`. Then [load them](#4-load-the-files-in-lighttouch).

Options:

- `--zip LightTouchMac-universal.zip` uses a ZIP you already downloaded.
- `--out DIR` writes somewhere else.
- `--verified-tools` also writes `verified-tools/`, the folder that
  [IPSW preparation](#5-prepare-a-system-from-an-ipsw-instead) needs.
- `--armv7` also writes `armv7.itpack` for the iPod touch 4G route.
- A newer upstream release has a different checksum; the script refuses it unless you pass
  `--any-zip`.

**Android:** run the script on a computer, then copy the three files to the phone
(USB, cloud drive, or anywhere the Android file picker can see).

## 2. By hand from the upstream app ZIP

1. Download **`LightTouchMac-universal.zip`** from the
   [upstream release](https://github.com/samhenrigold/LightTouchMac/releases/tag/v1.0-2026.10.15b).
2. Open it with any ZIP tool (Windows Explorer, `unzip`, 7-Zip, Archive Utility) and
   copy two files out of `Light Touch.app/Contents/Resources/Device/`:
   - `n72ap-7E18.itbase`
   - `bootrom_240_4`: rename your copy to **`bootrom.bin`**.
3. The guest tools are inside `Light Touch.app/Contents/Resources/Guest/guest.aar`, an
   Apple Archive. Ordinary ZIP tools cannot open it:
   - **macOS:** `aa extract -i guest.aar -d guest`, then copy `guest/guest-tools/armv6.itpack`.
   - **Linux / Windows:** `python3 tools/get-assets.py --zip LightTouchMac-universal.zip`
     unpacks it (this is route 1 without the download).

## 3. If you already have the files

Keep the `.itbase` as it is: do not unzip it, and do not rename an IPSW to `.itbase`.
The board, iOS build and guest architecture must match; for the iPod touch 4G route see
[route 5](#5-prepare-a-system-from-an-ipsw-instead).

## 4. Load the files in LightTouch

**Desktop (Linux, Windows, macOS):** in **iOS files**, choose `n72ap-7E18.itbase` in
**1 iOS system**, `armv6.itpack` in **2 Guest tools** and `bootrom.bin` in **3 Boot file**
(companion files in the same folder may fill in by themselves). Click **Set up device**,
wait for the import to finish, then **Start iOS**. Use **Stop** and wait for shutdown
before closing. For a system prepared on a Mac (the iPod touch 4G, [route 5](#5-prepare-a-system-from-an-ipsw-instead)),
use **Choose folder…** in **1 iOS system** and pick the packed `.itbase` folder, with `armv7.itpack`
as the guest tools and no boot file.

**Android:** you don't need these files: the app downloads and prepares iOS itself ([route 5](#5-prepare-a-system-from-an-ipsw-instead)).
To add files you already have, tap **I already have the device files** (on the welcome screen, or under the
device list of **Add a device**), choose `n72ap-7E18.itbase` as the **iOS system** and `bootrom.bin` as the **Boot
file** (or **Choose a folder** holding both), then **Create device**. The app adds its own guest tools.

The app checks the files itself; you never type hashes. Each saved device has its own
writable storage, and the imported system stays read-only, so one import can back several
devices (**New from selected** on desktop).

### Newer guest tools

Since v0.0.6, each [LightTouch release](https://github.com/blake-lucas/LightTouch/releases) attaches
its own `armv6.itpack` (the iPod touch 1G and 2G and the original iPhone), newer than the upstream app's;
games draw faster with it. Since v0.0.9 it also attaches `armv7.itpack`, for every other device. Since v0.0.7 the
Android app carries these guest tools and gives them to each new device itself; when an update of the app carries newer ones, an existing device gets them the next time it starts. Elsewhere, use the
attached file in place of the upstream one when you set up a device, or give it to an existing
device, which keeps its apps and data: on Android stop the device and tap **Settings › Update guest
tools**; on Linux run `lighttouch-linux --library LIB --record ID --set-guest-package
armv6.itpack`. The new tools install the next time iOS starts. Windows and macOS cannot update an
existing device yet.

## 5. Prepare a system from an IPSW instead

Preparation turns Apple's stock firmware into an emulator-ready system. Importing a stock IPSW in the iOS system picker does **not** do this. Every port prepares with the same tool, `firmwarekit` ([Portable/firmware](../Portable/firmware/README.md)), for any device and iOS version in the [firmware catalog](../Portable/firmware/firmware-catalog.json).

| Host | How |
| --- | --- |
| Android | In-app: **Prepare from an IPSW** |
| Linux | `firmwarekit create`, then **Choose folder…** on the packed `.itbase` |
| macOS | Upstream app's **Add Device**, or `firmwarekit` (its bundled copy or `scripts/build.sh firmwarekit`), then **Choose folder…** |
| Windows | Not yet: prepare on another computer, then copy the `.itbase` |

**Android:** tap **Get started** on first launch (or **Add a device** later), choose the device, then the iOS version. **Download** fetches the IPSW (from the link ipsw.me lists, else the catalog's mirrors and the Internet Archive) and checks it against the catalog; **Choose a file** uses one already on the phone. The iPod touch 2G also needs its boot file: it comes from another device on the phone when one has it, or **Download** fetches it from securerom.fun (GitHub as the fallback), checked against its SHA-256. **Prepare device** shows each step with a progress bar; the device is added when it finishes. On a Poco F3 the iPod touch 2G on iOS 3.1.3 takes about a minute; the iPod touch 4G on iOS 6.1.6 takes several minutes, because preparation starts iOS once so its first-start setup is done.

Every device and iOS version in the catalog can be chosen, marked **Tested** (checked on Un1box for Android),
**Tested, slow** (works, but slower than the iPod touch 2G on 3.1.3), **Experimental** (passes upstream's Mac checks),
**Untested**, or **Doesn't start yet** (the iPod touch 1G and the original iPhone, which stop at a white screen).

**Linux:**

```sh
scripts/build.sh linux firmwarekit
build/firmwarekit/linux/firmwarekit create --catalog Portable/firmware/firmware-catalog.json \
  --id n81ap-10B500 --ipsw iPod4,1_6.1.6_10B500_Restore.ipsw --out n81ap-10B500 \
  --helper build/firmwarekit/linux/lighttouch-oneshot --guest-tools GUEST-TOOLS
build/firmwarekit/linux/firmwarekit pack-base --base n81ap-10B500 --out n81ap-10B500.itbase
```

`lighttouch-oneshot` loads `build/linux/libqemu-arm.so` (or set `LTM_QEMU_DYLIB`). `GUEST-TOOLS` is the folder of guest tools preparation installs (`armv6.itpack`, `armv7.itpack`, `it_seal`, …): build it on a Mac with `scripts/build.sh firmware-tools`, or copy `assets/guest-tools` out of an Un1box APK. On a desktop PC the iPod touch 2G takes about 35 seconds and the 4G about 5½ minutes.

**macOS:** in the upstream app use **Add Device** for the board and build, or run its bundled tool directly:

```sh
APP="/Applications/Light Touch.app"
"$APP/Contents/MacOS/firmwarekit" create --catalog /path/to/firmware-catalog.json \
  --id n81ap-10B500 --ipsw /path/to/iPod4,1_6.1.6_10B500_Restore.ipsw --out n81ap-10B500-prepared
"$APP/Contents/MacOS/firmwarekit" pack-base --base n81ap-10B500-prepared --out n81ap-10B500.itbase
```

The iPod touch 2G's IPSW is `iPod2,1_3.1.3_7E18_Restore.ipsw`. Apple never hosted this update publicly and ipsw.me does not list it; the Internet Archive's [iOS Firmware Exhibit Collection](https://archive.org/download/iOS_Firmware_Exhibit_Collection) has it, and nearly every other IPSW the catalog names. Its boot file is the S5L8720's SecureROM (iBoot-240.4), mask ROM in the chip: no IPSW contains it. [securerom.fun](https://securerom.fun) publishes dumps (also in its GitHub repository, `hekapooios/hekapooios.github.io`, under `resources/APROM/`); its "s5l8720xsi, iBoot-240.4" file is byte-identical to upstream's `bootrom_240_4` (SHA-256 `55f4d8ea…ca054`). The iPod touch 4G needs **no** boot file: its prepared system boots from its own `kboot.bin`. See upstream's [preparation notes](https://github.com/samhenrigold/LightTouchMac/blob/v1.0-2026.10.15b/CONTRIBUTING.md#firmwares-in-the-catalog).

## File types

| File | Purpose |
| --- | --- |
| `.ipsw` | Apple's stock firmware; input to preparation only. |
| `.itbase` | A prepared, read-only system archive (includes `device.lock.json`). |
| `armv6.itpack` / `armv7.itpack` | Guest tools for the guest CPU. Preparation outputs may name the matched one `guest.itpack`. |
| `bootrom.bin` / `bootrom_240_4` | The n72 SecureROM. Not the same as the `iBoot.bin` inside a prepared system. |
| `kboot.bin` | The n81 boot file, already inside its prepared system. |
| `guest.aar` | Upstream's Apple Archive of guest tools, developer disk images and helpers. |

Building the guest tools yourself (`armv6.itpack` / `armv7.itpack` from qemu-ios `contrib/`): a Mac
with Xcode and the iPhoneOS 3.1.3 and 3.2 SDKs; the steps, where each input came from, and what does
not work are in qemu-ios `contrib/armv6-toolchain/README.md` ("Setting up a Mac to build the guest
package"). A record keeps the guest tools it was imported with until they are replaced: on Android, "Guest
tools" on a stopped device; on Linux, `lighttouch-linux --library LIB --record ID --set-guest-package
ITPACK`. A newer serial installs over the existing system at the next boot, keeping apps and data.

Storage note: alpha builds do not migrate old library layouts; keep the original `.itbase` so you can re-import.

Use firmware you are entitled to use, and keep firmware, ROMs and personal guest data out
of public bug reports and repositories.
