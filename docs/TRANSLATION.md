# 中文（简体）翻译

Un1box 的界面文字在源码里是英文，中文由 **一个词表** 决定，而不是把英文写死在每个界面里再改一遍。
这样界面代码保持原样（与上游 LightTouch 的差异尽量小），中文只有一处可维护的地方。

## 三个文件

| 文件 | 作用 |
| --- | --- |
| `I18n.kt` | 语言选择：跟随系统 / 简体中文 / English，以及 `I18n.t(text)` |
| `I18nZh.kt` | 词表：`exact`（整句、整词精确匹配）+ `rules`（带变量的句子，正则匹配） |
| `LocalizedText.kt` | 本包内的 `Text`：先把字符串交给 `I18n.t`，再按 Material3 原样绘制 |

`LocalizedText.kt` 里的 `Text` 与 `androidx.compose.material3.Text` 参数一一对应。
Kotlin 的解析顺序里"同包声明"优先于 `import androidx.compose.material3.*`，所以界面文件里的
`Text("Settings")` 会落到这里，不需要改任何界面代码，也不需要把 `Text` 换成一个新名字。

## 语言怎么选

设置 › **语言**（`ui_language`）三选一：

- **跟随系统**（默认）：系统语言以 `zh` 开头就用中文，否则英文。
- **简体中文** / **English**：固定一种。

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

- 词表能解析、没有重复键、没有空值、中文值里确实有中文；
- `%d` / `%s` / `%.2f` 这类占位符在译文里个数和种类不变；
- 每条规则的正则能编译、`$n` 不超过组数、锚定在开头；
- 界面里能显示的字符串有 91% 以上已翻译，且 `SCREENS` 列出的界面文件各自 ≥95%；
- 本包只有一个 `Text`（就是会翻译的那个），语言开关在设置里可达。

## 已知限制

- 无障碍读出的是 `Icon(contentDescription = "Settings")` 这类英文；`tr()` 已经备好，
  但逐个改调用点还没做。
- `PerformanceOverlay.kt` 显式导入了 `androidx.compose.material3.Text`（它画的是
  `AnnotatedString`），显式导入优先于同包声明，所以性能浮层里的标签不被翻译 ——
  那里全是数字和单位，本来也不需要。
- 少数引擎层的长诊断句仍是英文：它们只在出错时的错误卡里出现，且含大量内部字段名。
- UI 是语言切换后重建界面生效，不是即时重排。

## 这个仓库的译文怎么来的

`docs/TRANSLATION.md` 只描述结果；词表本身是手写的中文（不是机器直译），
专有名词按 Apple 中文文档的习惯（`iPod touch`、`iOS`、`IPSW` 不译），
界面用语取中国大陆常见说法（"设置 / 详细 / 继续 / 恢复 / 已验证"）。
