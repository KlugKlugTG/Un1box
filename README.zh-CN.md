# Un1box

<p align="center"><img src="./Picsart_26-10-25_03-23-04-384.jpg" alt="Un1box" width="700"></p>

[English](README.md) · **简体中文**

**Un1box** 是一个在手机上运行经典 iPhone OS 与 iOS 的 Android 应用：选一台设备、选一个版本，
在手机上准备它，然后一个真正可触摸的 iOS 设备就出现在窗口里。**目前仅支持 Android** ——
本仓库里的桌面版（Linux、Windows、macOS）仍以 **LightTouch** 的名字发布，没有改动。

Un1box 是 **[Light Touch](https://github.com/blake-lucas/LightTouch)** 的分支（更早还分自
[Light Touch for Mac](https://github.com/samhenrigold/LightTouchMac)），建立在共用的
[qemu-ios 引擎](https://github.com/blake-lucas/qemu-ios)之上。LightTouch 给了这个项目模拟器、
设备准备流程、引擎工作和绝大部分代码；Un1box 增加了自己的 Android 应用、自己的名字和自己的方向。
LightTouch 能做的这里都还能做：设置、商店、设备库和已准备系统的格式都一样，所以任意一方准备的设备，
另一方都能打开。

一个已准备的 iOS 系统可以支撑多个已保存的设备，每个设备有自己的存储。

界面提供**简体中文**：见 [docs/TRANSLATION.md](docs/TRANSLATION.md)。设置在「设置 › 语言」里，
默认跟随系统语言。

## 社区

Discord 服务器：https://discord.gg/ZpEkAV47H9
Telegram 频道：https://t.me/hyperhle

## 下载

[Un1box 构建](https://github.com/KlugKlugTG/Un1box/releases) —— CI 每次提交都会构建。
纯 LightTouch 版本（含桌面构建）在上游：[upstream](https://github.com/blake-lucas/LightTouch/releases/latest)，v0.0.13。

Un1box 仍处于早期，版本之间可能有变化。

## 平台

| 平台 | 构建形式 | 状态 |
| --- | --- | --- |
| **Android** | APK，ARM64（Android 10+） | **这就是 Un1box。** 主测试平台为 Poco F3 与 Pixel 10。首次启动会引导你在手机上从 IPSW 准备一台设备。图形、音频、触摸、倾斜、后置摄像头和 IPA 安装均可用；Legacy Store 可以像浏览网页一样浏览，并能在后台排队安装多个应用。 |
| Linux | x86_64 AppImage | LightTouch 前端，由本仓库构建。启动、输入、加速图形、音频、Legacy Store 和 IPA 安装均可用。用命令行 `firmwarekit` 准备设备。 |
| Windows | x86_64 ZIP | LightTouch Windows 预览版。启动、图形、音频和 Legacy Store 安装已有用户确认。还不能准备设备：请从其他电脑复制已准备好的 `.itbase`。 |
| macOS | Apple Silicon 应用 ZIP | 共用的桌面 UI 可以构建并打包。客户机服务尚未完全验证。需要 macOS 26。上游的 [Light Touch for Mac](https://github.com/samhenrigold/LightTouchMac/releases) 才是经过测试的 Mac 应用。 |

## 设备

| 设备 | iOS | 状态 |
| --- | --- | --- |
| iPod touch 2G | 3.1.3 | **已验证**：主要目标，最快。Flight Control、Jetpack Joyride 等游戏在近期手机上跑在 60 fps 或接近 60 fps。 |
| iPod touch 2G | 4.2.1 | **已验证** |
| iPod touch 3G | 4.2.1 | **已验证** |
| iPod touch 4G | 6.1.6 | **已验证**：在 Pixel 10 上，Infinity Blade 与 Infinity Blade II 全速运行，Epic Citadel 为 60 fps。其余多数游戏（Jetpack Joyride、Doodle Jump、Real Racing 等）在近期手机上为 60 fps 或接近 60 fps。 |
| iPhone 3GS | 5.1.1 | **已验证，较慢** |
| iPhone 4 | 4.2.1、4.3.5 | **已验证，较慢** |
| iPod touch 1G、初代 iPhone | iPhone OS 1 | **尚无法启动**：配置完成后停在白屏。这是上游的结论，2026-11-01 复核；未在 Un1box 上运行。 |
| [固件目录](Portable/firmware/firmware-catalog.json)中的其他设备 | iPhone OS 1 至 iOS 7 | 可以准备并尝试；尚未在 Un1box 上验证。[docs/COMPATIBILITY.md](docs/COMPATIBILITY.md) 记录了试过的结果。 |

## Android 快速上手

安装 APK，点 **开始使用**。选择设备和 iOS 版本，应用会下载 iOS 并在手机上准备它
（iPod touch 2G + iOS 3.1.3 约需一分钟）。你不需要自备任何文件。若你已有别处生成的设备文件，
点 **我已有设备文件**。

然后启动设备：屏幕下方的 Home、睡眠/唤醒和音量按键在显示区域下面，**停止** 会正常关闭 iOS。
每台设备保留自己的存储，已准备好的系统永远不会被改动。

由于 Un1box 使用自己的包名（`org.un1box.android`），Android 把它当作与 LightTouch 不同的应用：
它会安装在旁边而不是覆盖它，各自保留自己的已准备设备。

## 桌面版

解包构建产物，然后为它准备一个 iOS 系统。[资源指南](docs/ASSETS.md) 覆盖两种途径：

- **用 Apple 的 IPSW 自己准备**：在 Linux 或 macOS 上用 `firmwarekit`。所有平台使用同一个准备器
  （[Portable/firmware](Portable/firmware/README.md)）。
- **使用已准备好的系统**（`.itbase`）：例如 `tools/get-assets.py` 从上游 Mac 应用取来的那个，
  连同它的客户机工具（`armv6.itpack` 或 `armv7.itpack`），以及 iPod touch 2G 需要的 boot ROM。

## 性能

客户机通过 QEMU 的 TCG 动态翻译运行，客户机 CPU 没有硬件虚拟化。这个分支的引擎工作很大一部分是速度：
跨页链接、间接跳转缓存、更少的大锁等待，以及更快的 OpenGL ES 路径。历史与实测数据见
[docs/PERFORMANCE.md](docs/PERFORMANCE.md)。在 Android 上 vCPU 会被保持在手机的快速核心上。
性能浮层显示的是宿主侧的引擎负载和内存，不是 iOS 内部的 CPU 占用。

## 构建与贡献

`scripts/build.sh` 构建引擎、Linux 前端、Android APK、`firmwarekit`，以及在 Mac 上通过 ssh 构建客户机工具；
`scripts/build-release.sh` 构建四个平台的发布版。Windows 与 macOS 应用在各自主机上通过
`Desktop/build.py` 构建。[docs/BUILDING.md](docs/BUILDING.md) 说明输入，
[CONTRIBUTING.md](CONTRIBUTING.md) 说明仓库结构、固件准备、测试和代码风格。开发在
`dev/*` 分支上进行（本仓库与引擎），发布来自 `main`。

不需要构建、不需要设备、不需要 CI 的测试：

```sh
python3 tests/branding/run.py    # 名字：标签、包名、源码、JNI
python3 tests/idle/run.py        # 应用的空闲循环
python3 tests/mic/run.py         # 麦克风通路
python3 tests/camera/run.py      # 摄像头通路
python3 tests/gles/run.py        # 客户机的 GL 桥
python3 tests/i18n/run.py        # 简体中文词表与界面覆盖
```

后三个还接受 `--engine PATH` 指向一个 `qemu-ios` 检出，它们引擎侧的检查会从那里取要测的代码；
不提供时那些检查被跳过，其余仍然运行。

## 许可与致谢

Un1box 是自由软件，采用 GPL 第 2 版或（按你的选择）任何更新版本；见 [LICENSE](LICENSE)。
上游的作者身份与许可均予以保留。

它之所以存在，是因为 blake-lucas 的 **[Light Touch](https://github.com/blake-lucas/LightTouch)**，
而后者又建立在 Sam Henri Gold 的 [Light Touch for Mac](https://github.com/samhenrigold/LightTouchMac) 之上。
Un1box 的名字与 Android 应用是自己的；下面的模拟器是他们的。

它还建立在
[qemu-ios](https://github.com/samhenrigold/qemu-ios)、
[QEMU](https://www.qemu.org)、
[devos50 的 qemu-ios](https://github.com/devos50/qemu-ios)、
[libimobiledevice](https://libimobiledevice.org)、libusbmuxd、libplist、libtatsu、
上游的 usbmuxd 分支、iBoot32Patcher、FFmpeg、GLib、libslirp、PCRE2、pixman、
OpenSSL、Swift 及其 Foundation、swift-crypto、ZIPFoundation，以及 Unrar.swift 与 RARLAB 的 UnRAR 之上。
请保留各软件包随附的许可证与源码声明。

iPhone、iPod touch 和 iPad 是 Apple Inc. 的商标。Un1box 与 Apple 无关。
