# 翻译（简体中文 · Русский）

Un1box 的界面文字在源码里是英文，每种语言由 **自己的一个词表** 决定，而不是把英文写死在每个界面里再改一遍。
这样界面代码保持原样（与上游 LightTouch 的差异尽量小），一种语言只有一处可维护的地方。

## 四个文件

| 文件 | 作用 |
| --- | --- |
| `I18n.kt` | 语言选择：跟随系统 / 简体中文 / Русский / English，以及 `I18n.t(text)` |
| `I18nZh.kt` | 中文词表：`exact`（整句、整词精确匹配）+ `rules`（带变量的句子，正则匹配） |
| `I18nRu.kt` | 俄语词表：与中文词表同样的键，同样的 `exact` + `rules` 结构 |
| `LocalizedText.kt` | 本包内的 `Text`：先把字符串交给 `I18n.t`，再按 Material3 原样绘制 |

`LocalizedText.kt` 里的 `Text` 与 `androidx.compose.material3.Text` 参数一一对应。
Kotlin 的解析顺序里"同包声明"优先于 `import androidx.compose.material3.*`，所以界面文件里的
`Text("Settings")` 会落到这里，不需要改任何界面代码，也不需要把 `Text` 换成一个新名字。

## 语言怎么选

设置 › **语言**（`ui_language`）四选一：

- **跟随系统**（默认）：系统语言以 `zh` 开头用中文，以 `ru` 开头用俄语，否则英文。
- **简体中文** / **Русский** / **English**：固定一种。

选择存在 `AppSettings.language`，改动后界面会重建（`Activity.recreate()`），
`MainActivity.onCreate` 在绘制任何东西之前把手机语言和用户选择交给 `I18n`。

## 加一句新的界面文字

界面里照常写英文，然后在 `I18nZh.kt` 的 `exact` 里加一行：

```kotlin
put("Stop profile recording", "停止性能录制")
```

英文键必须和源码里逐字节一致（大小写、标点、结尾空格都算），否则这一条永远不会命中。

带变量的句子加一条规则，`$1`、`$2` 是捕获组，组里的内容还会再查一次词表（所以
`Tested: Checked on Un1box for Android.` 会得到「已验证：已在 Android 版 Un1box 上验证。」）：

```kotlin
Regex("^Stop (.+)\\?\$") to "停止 \$1？",
```

规则**从上往下**匹配，第一条命中的生效，所以具体句子要放在宽泛的句子前面
（`zh_rules2.py` 里那几条必须早于 `^Guest start failed: (.+)$`）。

## 刻意保留英文的东西

- 机型、芯片、文件：`iPod touch 2G`、`iPhone 4`、`Samsung S5L8900`、`PowerVR MBX Lite`、`Info.plist`
- 崩溃报告自身的标题与原文（`Binary Images:`、`Dyld Error Message:` 等），照抄 Apple 的格式便于比对
- 引擎与协议层日志（`EngineService` 的部分诊断、`HitchData` 导出的 TSV）
- 数值与单位：`16.9 ms`、`59 FPS`

这些不是漏译，是 `tests/i18n/run.py` 里 `KEEP_ENGLISH` / `KEEP_PATTERNS` 明说的例外。

## 验证

```sh
python3 tests/i18n/run.py      # 词表结构、占位符、规则、覆盖率、接线
python3 tests/i18n/run.py -v   # 外加每个文件的完成度和仍未翻译的字符串
```

测试不编译、不需要设备、不联网，和仓库里其他 `tests/*/run.py` 一样只读文本。它检查：

- 每个词表都能解析、没有重复键、没有空值、值里确实有该语言的文字（中文 / 西里尔字母）；
- `%d` / `%s` / `%.2f` 这类占位符在译文里个数和种类不变；
- 每条规则的正则能编译、`$n` 不超过组数、锚定在开头；
- 对**每个**词表：界面里能显示的字符串有 80% 以上已翻译，且 `SCREENS` 列出的界面文件各自 ≥95%；
- 本包只有一个 `Text`（就是会翻译的那个），语言开关在设置里可达。

## 俄语词表（I18nRu.kt）

俄语词表与中文词表同源：键逐字节相同（`tests/i18n/run.py` 会对两个词表分别核对覆盖率、
占位符和规则），只有值和日期写法不同 —— 固件列表里的 `March 2010` 中文写作 `2010 年 3 月`，
俄语写作 `март 2010 г.`。设置里的语言行会自动多出 `Русский`，因为它是 `UiLanguage` 的一项。

俄语由人工/助手翻译后经文本检查（覆盖率、占位符、规则锚定、`$n` 组数），尚未在设备上
逐屏校对；发现不自然的句子直接改 `I18nRu.kt` 的值即可，键不要动。

## 在真正的 Android 工程里编译与运行过

上面的脚本只看文本。词表本身也在真实工具链上过了一遍（AGP 8.9.2、Kotlin 2.1.20、
Compose 编译器插件、NDK 28.2，编译 `compileSdk 35`）：

```sh
./gradlew :app:compileDebugKotlin      # 零 error
./gradlew :app:testDebugUnitTest       # 79 个 JVM 单测全过
```

再把 AGP 编出来的 `org.un1box.android.I18nZh` 类直接拿来跑：从 `*.kt` 里取出全部
904 条界面能显示的字符串（`"a" + "b"` 已按运行时拼接合并、转义已展开），逐条交给
`I18nZh.translate`，结果写回文件核对。766 条变成中文；余下 138 条是：

- 95 条按设计保留英文（机型名、芯片名、单位、崩溃报告的原始标题）；
- 20 条是 `Icons.kt` 里 `icon("Mic", "M12 14c…")` 的图标内部名，不是屏幕上的字；
- 23 条是崩溃日志的解析模式（`Symbol not found:\s*(.+)`）和性能浮层的表头
  （`# sequence published-ns …`），给人看的是它们匹配到的内容，不是模式本身。

这个过程抓到了纯文本检查抓不到的东西：带 `$变量` 的句子在源码里只是模板，
只有跑起来才知道拼完是什么样，`"Camera: $reason; trying again"`、`"$asset is truncated"`
这类句子的译文因此补成了规则。

### 在 Windows 上构建的两个坑

与翻译无关，但会让 `./gradlew` 在 Windows 上直接失败：

1. `gradle/verification-metadata.xml` 记录了依赖的 SHA256，其中 `aapt2` 只有
   Linux/macOS 的产物。Windows 版 `aapt2-8.9.2-…-windows.jar` 不在清单里，
   `processDebugResources` 会以 "Dependency verification failed" 停下。
   本机构建加 `--dependency-verification=off` 即可（不要改这个文件，它保护的是真依赖）。
2. `aidl.exe` 会把自己被调用的命令行写进生成文件的注释里。Windows 路径含
   `…\org\un1box\…`，而 javac 在分词之前先处理 `\uXXXX` 转义，于是注释里的
   `\un1box` 被当成非法的 Unicode 转义，`compileDebugJavaWithJavac` 报错。
   在 Java 编译前把这一行注释删掉即可。这两个都只影响本机构建，源码不需要改。

完整 APK 还需要仓库外的两样东西：`qemu-ios` 源码（`LIGHTTOUCH_QEMU_SOURCE`）和
预编译引擎 `engineRoot/<abi>/libqemu-arm.so`，见 `scripts/build.env` 与 `docs/BUILDING.md`。

## 已知限制

- 无障碍读出的是 `Icon(contentDescription = "Settings")` 这类英文；`tr()` 已经备好，
  但逐个改调用点还没做。
- `PerformanceOverlay.kt` 显式导入了 `androidx.compose.material3.Text`（它画的是
  `AnnotatedString`），显式导入优先于同包声明，所以性能浮层里的标签不被翻译 ——
  那里全是数字和单位，本来也不需要。
- 仍有少量引擎层的诊断句不翻译：它们是崩溃日志的原始字段（`Dyld Error Message:`、
  `# sequence published-ns …`）和日志解析模式，属于要原样呈现的证据，不是界面用语。
- UI 是语言切换后重建界面生效，不是即时重排。

## 这个仓库的译文怎么来的

`docs/TRANSLATION.md` 只描述结果；词表本身是手写的中文（不是机器直译），
专有名词按 Apple 中文文档的习惯（`iPod touch`、`iOS`、`IPSW` 不译），
界面用语取中国大陆常见说法（"设置 / 详细 / 继续 / 恢复 / 已验证"）。
