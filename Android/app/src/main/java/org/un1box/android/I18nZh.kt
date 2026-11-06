package org.un1box.android

/**
 * The Simplified-Chinese catalogue: what the app says, in Chinese.
 *
 * [exact] holds whole labels and sentences, keyed by the English the composables pass to
 * [Text]. [rules] covers the ones with a changing part (a device name, a version, a
 * count); they are tried in order, so a specific sentence comes before a general one, and
 * a captured part is translated again when it is itself a known string ("Tested: ..." gets
 * the sentence after the colon too).
 *
 * Anything not listed falls through to English: a half-finished translation should read as
 * English, never as a blank or a mangled key.
 *
 * The English keys must stay byte for byte as the sources write them, or an entry quietly
 * stops matching. tests/i18n/run.py checks that.
 */
internal object I18nZh {
    private val exact: Map<String, String> = HashMap<String, String>(1414).apply {
        put("No", "否")
        put("OK", "好")
        put(" ms", " 毫秒")
        put("How", "方式")
        put("May", "5 月")
        put("Off", "关闭")
        put("Yes", "是")
        put(" FPS", " 帧/秒")
        put(" or ", " 或 ")
        put("Back", "返回")
        put("Chip", "芯片")
        put("Copy", "复制")
        put("Dark", "深色")
        put("Full", "完整")
        put("Home", "主屏幕")
        put("Info", "信息")
        put("July", "7 月")
        put("June", "6 月")
        put("More", "更多")
        put("Name", "名称")
        put("None", "无")
        put("Save", "保存")
        put("Stop", "停止")
        put(" and ", " 和 ")
        put("About", "关于")
        put("April", "4 月")
        put("Audio", "音频")
        put("Basic", "简略")
        put("Board", "主板")
        put("Build", "版本号")
        put("Clear", "清除")
        put("Close", "关闭")
        put("Graph", "图表")
        put("Known", "已知")
        put("Light", "浅色")
        put("March", "3 月")
        put("Model", "型号")
        put("Other", "其他")
        put("Pause", "暂停")
        put("Range", "范围")
        put("Ready", "就绪")
        put("Retry", "重试")
        put("Saved", "已保存")
        put("Share", "分享")
        put("Sharp", "锐利")
        put("Start", "开始")
        put("State", "状态")
        put("Theme", "主题")
        put("Total", "合计")
        put("Worst", "最差")
        put("1% low", "1% 低帧")
        put("A to Z", "A 到 Z")
        put("Accept", "接受")
        put("August", "8 月")
        put("Cancel", "取消")
        put("Charts", "榜单")
        put("Chosen", "已选择")
        put("Delete", "删除")
        put("Detail", "详细程度")
        put("Device", "设备")
        put("Found ", "已找到 ")
        put("Jetsam", "内存回收")
        put("Memory", "内存")
        put("Newest", "最新")
        put("Oldest", "最早")
        put("Paused", "已暂停")
        put("Record", "记录")
        put("Rename", "重命名")
        put("Report", "报告")
        put("Resume", "继续")
        put("Screen", "屏幕")
        put("Search", "搜索")
        put("Smooth", "平滑")
        put("System", "跟随系统")
        put("Tested", "已验证")
        put("Thread", "线程")
        put("Avg FPS", "平均 FPS")
        put("Copying", "正在复制")
        put("Details", "详情")
        put("Devices", "设备")
        put("Dismiss", "忽略")
        put("Display", "显示")
        put("GL call", "GL 调用")
        put("Hitches", "卡顿")
        put("Inspect", "检查")
        put("Install", "安装")
        put("January", "1 月")
        put("Not now", "稍后")
        put("October", "10 月")
        put("Payload", "Payload")
        put("Process", "进程")
        put("Recover", "恢复")
        put("Running", "运行中")
        put("Saving…", "正在保存…")
        put("Storage", "存储")
        put("Thread ", "线程 ")
        put("Unknown", "未知")
        put("Waiting", "等待中")
        put("detail ", "详情 ")
        put("All apps", "全部应用")
        put("Camera: ", "摄像头：")
        put("Controls", "控制")
        put("Crashed:", "崩溃：")
        put("December", "12 月")
        put("Download", "下载")
        put("Featured", "精选")
        put("February", "2 月")
        put("Graphics", "图形")
        put("Hardware", "硬件")
        put("Installs", "安装")
        put("Language", "语言")
        put("New Free", "最新免费")
        put("New Paid", "最新付费")
        put("November", "11 月")
        put("Position", "位置")
        put("Prepared", "已准备")
        put("Selected", "已选择")
        put("Settings", "设置")
        put("Starting", "正在启动")
        put("Tested: ", "已验证：")
        put("Top Free", "免费榜")
        put("Top Paid", "付费榜")
        put("Top left", "左上")
        put("Untested", "未验证")
        put("Version ", "版本 ")
        put("app list", "应用列表")
        put("this iOS", "此 iOS 版本")
        put("this one", "此版本")
        put(" Crashed:", " 崩溃：")
        put(" of flash", " 闪存")
        put("1 version", "1 个版本")
        put("Boot file", "启动文件")
        put("Cancelled", "已取消")
        put("Disk size", "磁盘容量")
        put("Downloads", "下载")
        put("Guest CPU", "客户机 CPU")
        put("Installed", "已安装")
        put("Local IPA", "本地 IPA")
        put("Lock hold", "持锁")
        put("Lock wait", "锁等待")
        put("Main loop", "主循环")
        put("No device", "没有设备")
        put("Preparing", "正在准备")
        put("Reinstall", "重新安装")
        put("Released ", "发布于 ")
        put("September", "9 月")
        put("Shut down", "关机")
        put("Top right", "右上")
        put("Try again", "重试")
        put("Unpacking", "正在解包")
        put("Volume up", "音量 +")
        put("boot file", "启动文件")
        put("no report", "无报告")
        put("not found", "未找到")
        put("not ready", "尚未就绪")
        put("unable to", "无法")
        put("Add device", "添加设备")
        put("Background", "背景")
        put("Camera off", "摄像头已关闭")
        put("Cancelling", "正在取消")
        put("Categories", "分类")
        put("Frame rate", "帧率")
        put("Frame time", "帧时间")
        put("GPU driver", "GPU 驱动")
        put("Identifier", "标识")
        put("Installed ", "已安装 ")
        put("Installing", "正在安装")
        put("Last crash", "上次崩溃")
        put("Measuring…", "正在测量…")
        put("Microphone", "麦克风")
        put("OS Version", "系统版本")
        put("Preparing ", "正在准备 ")
        put("Recording…", "正在录制…")
        put("Screenshot", "截屏")
        put("Show fewer", "收起")
        put("iOS system", "iOS 系统")
        put("warming up", "正在预热")
        put("Bottom left", "左下")
        put("Cancelling…", "正在取消…")
        put("Collections", "合集")
        put("Diagnostics", "诊断")
        put("Downloading", "正在下载")
        put("Engine logs", "引擎日志")
        put("Full screen", "全屏")
        put("Get started", "开始使用")
        put("Guest tools", "客户机工具")
        put("None found.", "没有找到。")
        put("Performance", "性能")
        put("Save & Stop", "保存并停止")
        put("Save report", "保存报告")
        put("Source code", "源代码")
        put("Termination", "终止")
        put("Volume down", "音量 −")
        put("Why it quit", "退出原因")
        put("Your device", "你的设备")
        put("back camera", "后置相机")
        put("Add a device", "添加设备")
        put("All licenses", "全部许可证")
        put("Bottom right", "右下")
        put("Cannot read ", "无法读取 ")
        put("Checking app", "正在检查应用")
        put("Downloading ", "正在下载 ")
        put("Experimental", "实验性")
        put("Frame times:", "帧时间：")
        put("Guest paused", "客户机已暂停")
        put("Hide details", "隐藏详情")
        put("In the queue", "队列中")
        put("Legacy Store", "经典商店")
        put("Start it now", "立即启动")
        put("Tested, slow", "已验证，较慢")
        put("Top Grossing", "收入榜")
        put("Un1box guest", "Un1box 客户机")
        put("Unpacking · ", "正在解包 · ")
        put("front camera", "前置相机")
        put("is truncated", "已被截断")
        put("no frame yet", "尚无画面")
        put("no such file", "文件不存在")
        put("Called from: ", "调用自：")
        put("Choose a file", "选择文件")
        put("Close session", "关闭会话")
        put("Couldn't load", "加载失败")
        put("Create device", "创建设备")
        put("Delete failed", "删除失败")
        put("Follow system", "跟随系统")
        put("Guest display", "客户机显示")
        put("Import failed", "导入失败")
        put("Main loop CPU", "主循环 CPU")
        put("Most archived", "收录最多")
        put("No frames yet", "还没有帧")
        put("On this phone", "本机占用")
        put("Rename device", "重命名设备")
        put("Rename failed", "重命名失败")
        put("Requires iOS ", "需要 iOS ")
        put("Running guest", "客户机运行中")
        put("Search failed", "搜索失败")
        put("Serial number", "序列号")
        put("Show controls", "显示控制条")
        put("Shutting down", "正在关机")
        put("Starting iOS…", "正在启动 iOS…")
        put("Tilt controls", "重力感应控制")
        put("Wi-Fi address", "Wi-Fi 地址")
        put("Worker exited", "工作进程已退出")
        put("a few minutes", "几分钟")
        put("Android driver", "Android 驱动")
        put("App in front: ", "前台应用：")
        put("Binary Images:", "二进制镜像：")
        put("Cannot create ", "无法创建 ")
        put("Clear finished", "清除已完成")
        put("Copying files…", "正在复制文件…")
        put("Crashed Thread", "崩溃线程")
        put("Device deleted", "设备已删除")
        put("Engine (ARM64)", "引擎（ARM64）")
        put("Exception Type", "异常类型")
        put("Inspect button", "检查按钮")
        put("Inspect frames", "检查帧")
        put("Install failed", "安装失败")
        put("Later snapshot", "更晚的快照")
        put("Motion sensors", "运动传感器")
        put("Needs recovery", "需要恢复")
        put("No apps found.", "没有找到应用。")
        put("Prepare device", "准备设备")
        put("Presenter work", "呈现耗时")
        put("Recommended · ", "推荐 · ")
        put("Screen scaling", "画面缩放")
        put("Shutting down…", "正在关机…")
        put("about a minute", "约一分钟")
        put("code signature", "代码签名")
        put("And prepares it", "并在本机准备好")
        put("Cancel download", "取消下载")
        put("Choose a device", "选择设备")
        put("Choose a folder", "选择文件夹")
        put("Detailed timing", "详细计时")
        put("Download failed", "下载失败")
        put("Engine (x86-64)", "引擎（x86-64）")
        put("Exception Codes", "异常代码")
        put("For this device", "适用于此设备")
        put("Frame inspector", "帧检查器")
        put("Haptic feedback", "触感反馈")
        put("Name (optional)", "名称（可选）")
        put("Prepared system", "已准备的系统")
        put("Select a device", "选择设备")
        put("Shown on screen", "已显示到屏幕")
        put("Slowest events:", "最慢的事件：")
        put("vCPU accounting", "vCPU 统计")
        put("Add device files", "添加设备文件")
        put("Cannot read file", "无法读取文件")
        put("Close and resume", "关闭并继续")
        put("Could not save: ", "无法保存：")
        put("Earlier snapshot", "更早的快照")
        put("Exit full screen", "退出全屏")
        put("Lock (BQL) waits", "锁（BQL）等待")
        put("New & Noteworthy", "新品推荐")
        put("Record a profile", "录制性能剖面")
        put("Android interface", "Android 接口")
        put("Choose the folder", "选择文件夹")
        put("Copying to device", "正在复制到设备")
        put("Doesn't start yet", "暂无法启动")
        put("Download failed: ", "下载失败：")
        put("Exception Subtype", "异常子类型")
        put("Finished installs", "已完成的安装")
        put("Installs finished", "安装已完成")
        put("No apps added yet", "还没有添加应用")
        put("None needed. The ", "无需启动文件。")
        put("None over 0.5 ms.", "没有超过 0.5 毫秒的。")
        put("Nothing found in ", "在以下位置没有找到任何内容：")
        put("Recover and start", "恢复并启动")
        put("Screenshot failed", "截屏失败")
        put("could not save it", "无法保存")
        put("no camera service", "无相机服务")
        put("640 × 960 (Retina)", "640 × 960（Retina）")
        put("About this setting", "关于此设置")
        put("Changes since then", "之后的改动")
        put("Checking the file…", "正在检查文件…")
        put("Download cancelled", "下载已取消")
        put("Hide beta versions", "隐藏测试版")
        put("Invalid entry name", "条目名称无效")
        put("Missing entry name", "缺少条目名称")
        put("No device selected", "未选择设备")
        put("Not in the archive", "归档中没有")
        put("Preparation failed", "准备失败")
        put("Required · .itbase", "必需 · .itbase")
        put("Share hitch report", "分享卡顿报告")
        put("Termination Reason", "终止原因")
        put("Un1box preparation", "Un1box 准备")
        put("Update guest tools", "更新客户机工具")
        put("Waiting to install", "等待安装")
        put("Worker unavailable", "工作进程不可用")
        put("timing unavailable", "计时不可用")
        put("  of which GL calls", "  其中 GL 调用")
        put("Adding guest tools…", "正在添加客户机工具…")
        put("Download cancelled.", "下载已取消。")
        put("Dyld Error Message:", "Dyld 错误信息：")
        put("Find apps and games", "查找应用和游戏")
        put("Performance overlay", "性能浮层")
        put("Present on a worker", "由工作线程呈现")
        put("Show in device view", "在设备视图中显示")
        put("Try adding it again", "重新添加")
        put("Un1box downloads it", "由 Un1box 下载")
        put("detailed timing off", "详细计时已关闭")
        put("iOS firmware (IPSW)", "iOS 固件（IPSW）")
        put("vCPU on a host core", "vCPU 在某个宿主核心上")
        put("waiting for frames…", "正在等待帧…")
        put("(iOS logged nothing)", "（iOS 没有记录任何内容）")
        put("ANGLE (experimental)", "ANGLE（实验性）")
        put("Cannot read the IPSW", "无法读取该 IPSW")
        put("Creating your device", "正在创建设备")
        put("Firmware preparation", "固件准备")
        put("Guest app services: ", "客户机应用服务：")
        put("Install an IPA file…", "安装 IPA 文件…")
        put("Installs in progress", "正在安装")
        put("Open source licenses", "开源许可证")
        put("Prepare from an IPSW", "从 IPSW 准备")
        put("Stopped unexpectedly", "意外停止")
        put("Use a different file", "使用其他文件")
        put("——— Crash report ———", "——— 崩溃报告 ———")
        put("——— Emulator log ———", "——— 模拟器日志 ———")
        put("  of which GL present", "  其中 GL 呈现")
        put("Camera frame failed: ", "摄像头帧失败：")
        put("Checking the download", "正在校验下载")
        put("Copying the boot file", "正在复制启动文件")
        put("Detailed frame timing", "详细帧计时")
        put("Headphones or speaker", "耳机或扬声器")
        put("Headphones plugged in", "已插入耳机")
        put("Preparation cancelled", "准备已取消")
        put("Recording the launch…", "正在记录启动过程…")
        put("Reusing the saved one", "复用已保存的那份")
        put("Search apps and games", "搜索应用和游戏")
        put("Select a device first", "请先选择设备")
        put("Shown on screen after", "之后显示到屏幕")
        put("Where do I get these?", "这些文件从哪里获取？")
        put("device must be one of", "设备必须是以下之一")
        put("iOS system, boot file", "iOS 系统、启动文件")
        put("Camera not started yet", "摄像头尚未启动")
        put("Cannot read the folder", "无法读取该文件夹")
        put("Copying the iOS system", "正在复制 iOS 系统")
        put("Faithful scaler timing", "精确缩放计时")
        put("Firmware worker exited", "固件工作进程已退出")
        put("Paused · tap to resume", "已暂停 · 点击继续")
        put("Prepared on a computer", "在电脑上准备好")
        put("Profile capture failed", "性能录制失败")
        put("Reading crash reports…", "正在读取崩溃报告…")
        put("Recover retained state", "恢复保留的状态")
        put("Select a device first.", "请先选择设备。")
        put("Stop profile recording", "停止性能录制")
        put("is not a guest package", "不是客户机工具包")
        put("the file did not match", "文件不匹配")
        put("vCPU halted or outside", "vCPU 已停止或不在其中")
        put("Cannot write screenshot", "无法写入截屏")
        put("Cannot write the report", "无法写入报告")
        put("Classic iOS for Android", "Android 上的经典 iOS")
        put("Copying the iOS system…", "正在复制 iOS 系统…")
        put("Could not save the IPSW", "无法保存该 IPSW")
        put("Could not use that IPSW", "无法使用该 IPSW")
        put("Couldn't load the chart", "榜单加载失败")
        put("Device selection failed", "设备切换失败")
        put("Display input is paused", "显示输入已暂停")
        put("Guest tools not updated", "客户机工具未更新")
        put("Needs a newer processor", "需要更新的处理器")
        put("Unpause when you return", "返回时自动继续")
        put("Applies to the whole app", "会影响整个应用")
        put("Cannot create screenshot", "无法创建截屏")
        put("Cannot start preparation", "无法开始准备")
        put("Checking the iOS system…", "正在检查 iOS 系统…")
        put("Guest worker unavailable", "客户机工作进程不可用")
        put("No guest frame available", "没有可用的客户机帧")
        put("Not made for this device", "不适用于此设备")
        put("Not tested on Un1box yet", "尚未在 Un1box 上验证")
        put("Packing the iOS storage…", "正在打包 iOS 存储…")
        put("Prepared output retained", "已保留准备结果")
        put("Screen off · tap to wake", "屏幕已关闭 · 点击唤醒")
        put("iOS stopped unexpectedly", "iOS 意外停止")
        put("Cannot read the boot file", "无法读取启动文件")
        put("Device worker unavailable", "设备工作进程不可用")
        put("Display ownership retired", "显示所有权已作废")
        put("Frame history unavailable", "帧历史不可用")
        put("Graphics provider (ANGLE)", "图形提供者（ANGLE）")
        put("Guest code (vCPU running)", "客户机代码（vCPU 运行中）")
        put("Guest tools not updated: ", "客户机工具未更新：")
        put("Last Exception Backtrace:", "上次异常调用栈：")
        put("Slow events in this frame", "本帧内的慢事件")
        put("Unpacking the iOS system…", "正在解包 iOS 系统…")
        put("Device library unavailable", "设备库不可用")
        put("Invalid guest audio format", "客户机音频格式无效")
        put("Invalid guest iOS version.", "客户机 iOS 版本无效。")
        put("Loading the firmware list…", "正在加载固件列表…")
        put("No prepared iOS system in ", "以下位置没有已准备好的 iOS 系统：")
        put("Setting up the iOS system…", "正在配置 iOS 系统…")
        put("Shutdown was not confirmed", "未能确认关机")
        put("Why did the last app quit?", "上一个应用为什么退出？")
        put("the download stopped early", "下载提前中断")
        put("warming up (counter reset)", "正在预热（计数器已重置）")
        put("Could not create the device", "无法创建设备")
        put("Couldn't reach Legacy Store", "无法连接经典商店")
        put("Firmware preparation failed", "固件准备失败")
        put("Firmware worker unavailable", "固件工作进程不可用")
        put("Follow the phone's language", "跟随手机的语言")
        put("Guest worker restart failed", "客户机工作进程重启失败")
        put("Include apps that won't run", "包含无法运行的应用")
        put("License could not be read: ", "许可证读取失败：")
        put("Not available in this build", "此版本中不可用")
        put("Slow engine events in range", "区间内的引擎慢事件")
        put("That file is not a boot ROM", "该文件不是启动 ROM")
        put("iPod touch (2nd generation)", "iPod touch（第二代）")
        put("iPod touch (3rd generation)", "iPod touch（第三代）")
        put("iPod touch (4th generation)", "iPod touch（第四代）")
        put("the camera was disconnected", "摄像头已断开")
        put("Could not use that boot file", "无法使用该启动文件")
        put("Couldn't load the categories", "分类加载失败")
        put("Device library unavailable: ", "设备库不可用：")
        put("Download no longer available", "下载链接已失效")
        put("Experimental cache selection", "实验性缓存选择")
        put("Prepare from an IPSW instead", "改为从 IPSW 准备")
        put("Start the device to install.", "启动设备后才能安装。")
        put("Un1box finds the files in it", "Un1box 会在其中自动查找文件")
        put(", and the device's boot file.", "，以及该设备的启动文件。")
        put("Couldn't load the collections", "合集加载失败")
        put("Firmware worker unavailable: ", "固件工作进程不可用：")
        put("Invalid universal executable.", "通用可执行文件无效。")
        put("Notice integrity check failed", "声明完整性校验失败")
        put("The IPA directory is damaged.", "IPA 目录已损坏。")
        put("What the step is doing — 45 s", "当前步骤 — 45 秒")
        put("the guest has named no camera", "客户机没有指定相机")
        put("warming up (sampling resumed)", "正在预热（采样已恢复）")
        put("Cannot read the selected file.", "无法读取所选文件。")
        put("Checked on Un1box for Android.", "已在 Android 版 Un1box 上验证。")
        put("Firmware worker restart failed", "固件工作进程重启失败")
        put("Guest audio format unavailable", "客户机音频格式不可用")
        put("Licenses could not be loaded: ", "许可证加载失败：")
        put("the camera could not be opened", "无法打开摄像头")
        put("Audio output did not initialize", "音频输出未能初始化")
        put("Choose a device and iOS version", "选择设备和 iOS 版本")
        put("Could not publish imported file", "无法保存导入的文件")
        put("Could not publish the download.", "无法保存下载的文件。")
        put("Detailed timing could not start", "详细计时无法启动")
        put("I already have the device files", "我已有设备文件")
        put("Not compatible with this device", "与此设备不兼容")
        put("This file is not a ZIP archive.", "该文件不是 ZIP 归档。")
        put("Touch timestamp moved backwards", "触摸时间戳倒退了")
        put("exceeds the guest package limit", "超出客户机工具包的大小上限")
        put("the guest is on the back camera", "客户机正在使用后置相机")
        put("Could not finish profile capture", "未能完成性能录制")
        put("Could not read the frame history", "无法读取帧历史")
        put("Couldn't read this device's apps", "无法读取此设备上的应用")
        put("Display input generation retired", "显示输入的代次已作废")
        put("Filtered by the display hardware", "由显示硬件做滤波")
        put("Nothing here runs on this device", "这里没有能在此设备上运行的内容")
        put("Start the device to see its apps", "启动设备后才能查看其应用")
        put("Start the guest to install apps.", "启动客户机后才能安装应用。")
        put("The app executable is truncated.", "应用的可执行文件被截断。")
        put("The app's Info.plist is damaged.", "应用的 Info.plist 已损坏。")
        put("The app's executable is missing.", "应用的可执行文件缺失。")
        put("Touch timestamp must be positive", "触摸时间戳必须为正")
        put("Versions that run on this device", "可在此设备上运行的版本")
        put("the guest is on the front camera", "客户机正在使用前置相机")
        put("Application Specific Information:", "应用特定信息：")
        put("Couldn't check the Legacy Store: ", "无法查询经典商店：")
        put("Encrypted — cannot open in Un1box", "已加密 — 无法在 Un1box 中打开")
        put("Invalid executable load commands.", "可执行文件的加载命令无效。")
        put("Not checked for compatibility yet", "尚未检查兼容性")
        put("Record the next app launch (30 s)", "记录下一次应用启动（30 秒）")
        put("Source page could not be opened: ", "无法打开源码页面：")
        put("This copy is no longer available.", "该副本已不可用。")
        put("Touch timestamp ordering overflow", "触摸时间戳排序溢出")
        put("Could not create profile directory", "无法创建性能数据目录")
        put("Crisp pixels, then filtered to fit", "锐利像素，再滤波缩放到合适大小")
        put("Device worker exited during import", "导入过程中设备工作进程退出")
        put("Fewer pauses when opening iOS apps", "打开 iOS 应用时减少停顿")
        put("The app executable is unsupported.", "不支持该应用的可执行文件。")
        put("touch dropped: no input generation", "触摸被丢弃：无输入代次")
        put("A fresh firmware worker is required", "需要新的固件工作进程")
        put("Could not create a download folder.", "无法创建下载文件夹。")
        put("Couldn't check for newer versions. ", "无法检查新版本。")
        put("Keep Un1box open until it finishes.", "请保持 Un1box 打开，直到完成。")
        put("This app has no catalog identifier.", "该应用没有目录标识。")
        put("skipped (a newer frame replaced it)", "已跳过（被更新的帧替换）")
        put("——— iOS system log, last launch ———", "——— iOS 系统日志，上次启动 ———")
        put("Invalid universal executable slices.", "通用可执行文件的切片无效。")
        put("Phone volume keys control the device", "手机音量键控制设备音量")
        put("Tap to return; Stop ends this worker", "点按可返回；「停止」会结束这个工作进程")
        put("The IPA has a damaged member header.", "IPA 中存在损坏的成员头。")
        put("Touch timestamp tie budget exhausted", "触摸时间戳相同值的预算已用尽")
        put("Named after the model and iOS version", "按机型和 iOS 版本自动命名")
        put("This copy belongs to a different app.", "该副本属于另一个应用。")
        put("Could not identify the current iOS app", "无法识别当前 iOS 应用")
        put("Installed. Find it on the Home screen.", "已安装，可在主屏幕上找到。")
        put("Legacy Store sent an unreadable chart.", "经典商店返回了无法解析的榜单。")
        put("The IPA has a damaged ZIP64 directory.", "IPA 的 ZIP64 目录已损坏。")
        put("This copy is not made for this device.", "该副本不是为这台设备制作的。")
        put("Touch source timestamp moved backwards", "触摸源时间戳发生倒退")
        put("this phone refused the capture session", "手机拒绝了这次拍摄会话")
        put("Couldn't read the apps on this device: ", "无法读取此设备上的应用：")
        put("Prepared on a computer, then added here", "在电脑上准备好，然后添加到这里")
        put("The download exceeds its expected size.", "下载内容超出预期大小。")
        put("\nPrepared on a computer, then added here", "\n在电脑上准备好，再添加到这里")
        put("A minute or a few, with 1.5 to 5 GB free", "约一两分钟，需要 1.5–5 GB 可用空间")
        put("Could not stage the built-in guest tools", "无法准备内置的客户机工具")
        put("From an IPSW, or a device image you have", "从 IPSW，或你已有的设备镜像")
        put("Guest display ownership was not admitted", "客户机显示的所有权未被接受")
        put("Needs hardware this device does not have", "需要此设备没有的硬件")
        put("No active guest frame; gesture cancelled", "没有活动的客户机帧，手势已取消")
        put("Select a regular IPA smaller than 2 GiB.", "请选择小于 2 GiB 的普通 IPA。")
        put("unknown (the guest tools did not answer)", "未知（客户机工具未响应）")
        put("Legacy Store sent an unreadable app list.", "经典商店返回了无法解析的应用列表。")
        put("Legacy Store sent an unreadable response.", "经典商店返回了无法解析的响应。")
        put("Looking for this app in the Legacy Store…", "正在经典商店中查找该应用…")
        put("No chart was archived for this selection.", "该选项没有归档榜单。")
        put("Profile retained with incomplete metadata", "已保留性能数据，但元信息不完整")
        put("The IPA contains an unsafe symbolic link.", "IPA 中含有不安全的符号链接。")
        put("The app's Info.plist is not a dictionary.", "应用的 Info.plist 不是字典。")
        put("The guest did not report its device type.", "客户机没有报告它的设备类型。")
        put("——— Serial console (guest-serial.log) ———", "——— 串口控制台（guest-serial.log） ———")
        put("Legacy Store sent an unreadable app entry.", "经典商店返回了无法解析的应用条目。")
        put("Start profiling while the guest is running", "客户机运行时才能开始性能录制")
        put("Start the device to record its system log.", "启动设备后才能记录其系统日志。")
        put("The IPA directory is too large or damaged.", "IPA 目录过大或已损坏。")
        put("The archive supplied an invalid file size.", "归档提供了无效的文件大小。")
        put("Legacy Store sent an unreadable collection.", "经典商店返回了无法解析的合集。")
        put("No version of this app runs on this device.", "该应用没有可在此设备上运行的版本。")
        put("Start the device to read its crash reports.", "启动设备后才能读取其崩溃报告。")
        put("The catalog supplied an unexpected address.", "目录提供了意外的地址。")
        put("Debug diagnosis is unavailable in this build", "此版本不提供调试诊断")
        put("The catalog response exceeds the size limit.", "目录响应超出大小限制。")
        put("The guest stopped. Start it to install apps.", "客户机已停止，启动后才能安装应用。")
        put("The new device did not appear in the library", "新设备没有出现在设备库中")
        put("The unpacked IPA exceeds the supported size.", "解包后的 IPA 超出支持的大小。")
        put("Could not create the preparation tools folder", "无法创建设备准备工具文件夹")
        put("Guest worker unavailable; input was cancelled", "客户机工作进程不可用，输入已取消")
        put("The archived copy no longer matches this app.", "归档副本已与该应用不匹配。")
        put("The device gets no camera from the next start", "下次启动后设备不再使用摄像头")
        put("The downloaded IPA failed its checksum check.", "下载的 IPA 校验和检查失败。")
        put("The guest returned an incomplete app identity", "客户机返回的应用标识不完整")
        put("Guest worker unavailable; display was detached", "客户机工作线程不可用；显示已分离")
        put("Legacy Store does not support this device yet.", "经典商店还不支持此设备。")
        put("Legacy Store sent an unreadable category list.", "经典商店返回了无法解析的分类列表。")
        put("The Internet Archive is busy; try again later.", "Internet Archive 正忙，请稍后重试。")
        put("The archive supplied an invalid file checksum.", "归档提供了无效的文件校验和。")
        put("The catalog redirected to an insecure address.", "目录重定向到了不安全的地址。")
        put("The catalog supplied an invalid HTTPS address.", "目录提供了无效的 HTTPS 地址。")
        put("The minimum iOS version could not be verified.", "无法验证最低 iOS 版本要求。")
        put("This app is encrypted; select a decrypted IPA.", "该应用已加密，请选择已解密的 IPA。")
        put("This archived download is no longer available.", "该归档下载已不可用。")
        put("A tap when you press Home, Sleep/Wake or volume", "按主屏幕、睡眠/唤醒或音量键时给一次轻微振动")
        put("App installation is not included in this build.", "此版本不包含应用安装功能。")
        put("Guest worker unavailable; Start a fresh worker.", "客户机工作进程不可用；请重新启动。")
        put("No active guest session; touch was not admitted", "没有活动的客户机会话，触摸未被接受")
        put("Nobody has tried these yet. They may not start.", "还没有人试过这些版本，它们可能无法启动。")
        put("The IPA uses an unsupported compression method.", "IPA 使用了不支持的压缩方式。")
        put("Your device is prepared. Open Un1box to add it.", "设备已准备完成。打开 Un1box 添加它。")
        put("Include apps that won't run to see every result.", "包含无法运行的应用以查看全部结果。")
        put("Legacy Store returned a different archived copy.", "经典商店返回了不同的归档副本。")
        put("Legacy Store sent an unreadable collection list.", "经典商店返回了无法解析的合集列表。")
        put("This IPA needs an unsupported executable format.", "该 IPA 需要不受支持的可执行格式。")
        put("This app needs a newer processor than the guest.", "该应用需要的处理器比客户机更新。")
        put("Un1box is still starting; try again in a moment.", "Un1box 仍在启动，请稍后再试。")
        put("Un1box needs Apple's stock IPSW for this version", "此版本需要 Apple 原版 IPSW")
        put("Legacy Store sent unreadable version information.", "经典商店返回了无法解析的版本信息。")
        put("The IPA contains a symbolic link outside its app.", "IPA 含有指向应用之外的符号链接。")
        put("The prepared device is kept; try adding it again.", "已准备的设备仍然保留，请重新添加。")
        put("This app is not made for the guest device family.", "该应用不是为客户机机型制作的。")
        put("Un1box needs Apple's stock IPSW for this version.", "此版本需要 Apple 原版 IPSW。")
        put("Camera unavailable: Android has no camera service.", "摄像头不可用：Android 没有相机服务。")
        put("Guest app services are not included in this build.", "此版本不包含客户机应用服务。")
        put("The app has invalid bundle or executable metadata.", "应用的 bundle 或可执行元信息无效。")
        put("This copy has not been analyzed for compatibility.", "尚未分析该副本的兼容性。")
        put("Guest tools updated; they install at the next start", "客户机工具已更新，下次启动时安装")
        put("The app executable targets an unsupported platform.", "应用可执行文件的目标平台不受支持。")
        put("The crash report doesn't say where the app crashed.", "崩溃报告未说明应用是在哪里崩溃的。")
        put("Not shut down cleanly; it recovers at the next start", "未正常关机；下次启动时会恢复")
        put("Stop the running device to switch to or add another.", "停止正在运行的设备后才能切换或新增。")
        put("The apps on a device are read from it while it runs.", "设备上的应用在运行时才会被读取。")
        put("This build of Un1box has no device preparation tools", "此版本的 Un1box 不包含设备准备工具")
        put("Touch trace selection requires a fresh unused worker", "触摸轨迹选择需要新的、未使用过的工作线程")
        put("Preparing a device; return to view progress or cancel", "正在准备设备；返回可查看进度或取消")
        put("This IPA does not contain a supported ARM executable.", "该 IPA 不含受支持的 ARM 可执行文件。")
        put("Un1box sets up a device for you, right on this phone:", "Un1box 就在这台手机上为你准备好一台设备：")
        put("Camera unavailable: this phone reports no back camera.", "摄像头不可用：本机没有后置摄像头。")
        put("Reading a device's apps is not included in this build.", "此版本不包含读取设备应用的功能。")
        put("The first start takes longer while iOS sets itself up.", "首次启动会慢一些，iOS 需要自我配置。")
        put("Display input outside guest viewport; gesture cancelled", "显示输入落在客户机视野之外，手势已取消")
        put("Microphone unavailable: the recorder did not initialize", "麦克风不可用：录音器未能初始化")
        put("The device gets no microphone input from the next start", "下次启动后设备不再使用麦克风")
        put("The preparation worker stopped unexpectedly. Try again.", "准备工作进程意外停止，请重试。")
        put("This copy needs a newer processor than this device has.", "该副本需要比此设备更新的处理器。")
        put("Select an IPA containing one Payload app and Info.plist.", "请选择含一个 Payload 应用和 Info.plist 的 IPA。")
        put("This phone's cameras feed the device from the next start", "下次启动后由本机摄像头向设备提供画面")
        put("Also show it on the drawn device, not only in full screen", "在绘制的设备上也显示，而不只是全屏时")
        put("Stops at a white screen after it's set up. Not fixed yet.", "配置完成后停在白屏，尚未修复。")
        put("Titles in grey haven't been preserved in the archive yet.", "灰色标题表示归档中尚未收录。")
        put("Graphics provider selection requires a fresh unused worker", "图形提供者选择需要新的、未使用过的工作线程")
        put("Previous guest worker has not exited. Retry after it stops.", "上一个客户机工作进程尚未退出，停止后再重试。")
        put("Your phone's motion sensors move the device's accelerometer", "用手机的运动传感器驱动设备的加速度计")
        put("The IPA contains unsafe, duplicate or encrypted ZIP members.", "IPA 中含有不安全、重复或加密的 ZIP 成员。")
        put("The download is incomplete or differs from the archive size.", "下载不完整，或与归档大小不符。")
        put("The guest did not answer in time; the result is unconfirmed.", "客户机未及时响应，结果无法确认。")
        put("The iPod touch 2G on iOS 3.1.3 is the easiest place to start", "从 iPod touch 2G + iOS 3.1.3 起步最省事")
        put("This phone's microphone feeds the device from the next start", "下次启动后由本机麦克风向设备提供输入")
        put("Guest USB bridge did not create its sockets within 5 seconds.", "客户机 USB 桥未能在 5 秒内创建套接字。")
        put("These may not start, or may not work fully. Try them and see.", "这些可能无法启动，或功能不全，可以自行尝试。")
        put("Android stopped the preparation before it finished. Try again.", "Android 在完成前终止了准备，请重试。")
        put("Classic iPod touch and iPhone software, running on your phone.", "经典 iPod touch 与 iPhone 软件，跑在你这台手机上。")
        put("Previous firmware worker has not exited. Retry after it stops.", "上一个固件工作进程尚未退出，停止后再重试。")
        put("The vCPU spent much of this frame waiting for the engine lock.", "这一帧里 vCPU 大部分时间在等待引擎锁。")
        put("GL calls took most of this frame: host driver or readback cost.", "这一帧大部分时间花在 GL 调用上：宿主驱动或回读的开销。")
        put("Pick the device to emulate. You'll choose its iOS version next.", "选择要模拟的设备，接下来再选 iOS 版本。")
        put("Without the camera permission the emulated device has no camera.", "没有相机权限时，模拟设备就没有摄像头。")
        put("Microphone unavailable: this phone refused 44.1 kHz mono capture.", "麦克风不可用：本机拒绝了 44.1 kHz 单声道采集。")
        put("This copy is encrypted or has not been classified as installable.", "该副本已加密，或尚未判定为可安装。")
        put("This worker is disposing; vCPU accounting requires a fresh worker", "该工作线程正在结束；vCPU 统计需要新的工作线程")
        put("Tested versions are checked on Un1box; the others are yours to try.", "已验证版本都在 Un1box 上测过，其余的可自行尝试。")
        put("The guest was busy for the whole frame: more guest work than usual.", "整帧客户机都在忙：比平常做了更多工作。")
        put("Without the microphone permission the emulated device has no input.", "没有麦克风权限时，模拟设备就没有输入。")
        put("This worker is disposing; recovery and Start require a fresh worker.", "该工作线程正在结束；恢复与启动需要新的工作线程。")
        put("Camera unavailable: this phone offers no YUV size the device can use.", "摄像头不可用：本机没有设备可用的 YUV 尺寸。")
        put("Capture engine timing for the app on screen, for tools/perf/profile.py", "为当前屏幕上的应用采集引擎计时数据，供 tools/perf/profile.py 使用")
        put("This device needs no boot file. Un1box's guest tools are added for you.", "此设备不需要启动文件，Un1box 会自动加入客户机工具。")
        put("Apps you install from the Legacy Store or from an IPA file show up here.", "从经典商店或 IPA 文件安装的应用会显示在这里。")
        put("Required for iPod touch 1G and 2G, and the original iPhone · bootrom.bin", "iPod touch 1G/2G 与初代 iPhone 必需 · bootrom.bin")
        put("iOS comes from Apple, or the Internet Archive when Apple no longer has it", "iOS 来自 Apple；Apple 已下架的则来自 Internet Archive")
        put("As on the real device; Un1box emulates it, so speed depends on your phone.", "与真机相同；由 Un1box 模拟，因此速度取决于你的手机。")
        put("Cancel preparation and wait for a terminal result before closing the worker", "先取消准备并等待最终结果，再关闭工作进程")
        put("Guest start request was lost; select Start or Recover retained state again.", "启动请求丢失了；请再次点「开始」或「恢复保留的状态」。")
        put("Without the boot file this device will not start; you can still add it now.", "没有启动文件，此设备无法启动；现在仍可添加。")
        put("64 KB, part of the chip rather than the IPSW. The same for every iOS version.", "64 KB，属于芯片而非 IPSW，各 iOS 版本都相同。")
        put("The emulator stopped. The device's data is kept; press Start to run it again.", "模拟器已停止。设备数据已保留，点「开始」可再次运行。")
        put("Un1box needs Apple's stock IPSW for this version, and the device's boot file.", "此版本需要 Apple 原版 IPSW，以及设备的启动文件。")
        put("Checked on Un1box for Android, but slower than the iPod touch 2G on iOS 3.1.3.", "已在 Android 版 Un1box 上验证，但比 iPod touch 2G + iOS 3.1.3 慢。")
        put("Its apps, settings and data are removed from this phone. This can't be undone.", "它的应用、设置和数据会从这台手机上删除，且无法撤销。")
        put("Starts in Light Touch for Mac's checks; not yet checked on Un1box for Android.", "在 Light Touch for Mac 的检查中可启动；尚未在 Android 版 Un1box 上验证。")
        put("This worker is disposing; experimental cache selection requires a fresh worker", "该工作线程正在结束；实验性缓存选择需要新的工作线程")
        put("Start and recovery require a fresh unused guest worker. Stop this worker first.", "启动与恢复需要新的、未使用过的客户机工作线程。请先停止当前工作线程。")
        put("This device also needs its boot file; add it from Settings after it is created.", "此设备还需要启动文件；创建完成后可在设置中添加。")
        put("Recording the system log for 30 seconds. Open the app now and wait for it to quit.", "正在记录 30 秒系统日志。现在打开应用，等它自己退出。")
        put("Choose a device and iOS version; Un1box downloads Apple's firmware and builds it here", "选择设备和 iOS 版本；Un1box 下载 Apple 固件并在此构建")
        put("Space iOS frees is not given back to your phone yet, so a device's changes only grow.", "iOS 释放的空间暂时不会归还手机，因此设备的改动只会持续增长。")
        put("iOS rejected the app's code signature and closed it. This is not an emulation problem.", "iOS 拒绝了应用的代码签名并将其关闭。这不是模拟导致的问题。")
        put("Off, they set your phone's volume as usual; the device's own volume buttons are on screen", "关闭时音量键照常调手机音量；设备自身的音量键在屏幕上")
        put("A button that pauses iOS and opens the frame inspector, to pick apart a hitch you just saw", "一个暂停 iOS 并打开帧检查器的按钮，用来分析刚看到的卡顿")
        put("Camera off: Un1box has no camera permission. Turn the switch off and on again to grant it.", "摄像头已关闭：Un1box 没有相机权限。把开关关掉再打开即可授权。")
        put("The emulator stopped unexpectedly. The device's data is kept; press Start to run it again.", "模拟器意外停止。设备数据已保留，点「开始」可再次运行。")
        put("Detailed timing: on (selection.tsv has the per-frame breakdown, events.tsv the raw records)", "详细计时：开（selection.tsv 是逐帧明细，events.tsv 是原始记录）")
        put("The vCPU was runnable but often off-CPU: the phone scheduled other work or slowed the core.", "vCPU 可运行但常常不在 CPU 上：手机调度了其他工作，或降低了核心频率。")
        put("Un1box can't build this version on a phone yet. Prepare it on a computer, then add it here.", "Un1box 还无法在手机上构建这个版本。请先在电脑上准备好，再添加到这里。")
        put("Search the archive of classic iOS apps. Versions that run on this device are picked for you.", "在经典 iOS 应用归档中搜索，会自动挑出可在此设备上运行的版本。")
        put("Save & Stop keeps everything as it is, open apps included, and picks up right here next time.", "「保存并停止」会原样保留一切（包括已打开的应用），下次从这里继续。")
        put("The guest has not drawn enough frames to inspect. Resume, play for a few seconds and try again.", "客户机绘制的帧还不够，无法检查。继续运行几秒后再试。")
        put("Detailed timing: off (turn it on in Settings > Diagnostics for per-frame GL, CPU and lock timing)", "详细计时：关（在 设置 > 诊断 中开启，才能看到逐帧的 GL、CPU 与锁耗时）")
        put("Downloads come from Apple's servers when ipsw.me lists them, otherwise from the Internet Archive.", "ipsw.me 有记录时从 Apple 服务器下载，否则从 Internet Archive 下载。")
        put("The Legacy Store doesn't have this app, so there is no page for it and no newer version to offer.", "经典商店没有这个应用，因此没有对应页面，也没有可提供的新版本。")
        put("Hand-picked by the archive. Whether a version runs on this device is checked when you open an app.", "由归档精选。某个版本能否在此设备运行，会在打开应用时检查。")
        put("Microphone off: Un1box has no microphone permission. Turn the switch off and on again to grant it.", "麦克风已关闭：Un1box 没有麦克风权限。把开关关掉再打开即可授权。")
        put("iOS shuts down like holding the power button: apps close and the device turns off. Your apps and data are kept.", "iOS 会像长按电源键那样关机：应用关闭、设备断电，应用与数据都会保留。")
        put("iOS closed the app because it took too long to start. The emulator was too slow; the app itself is probably fine.", "iOS 因应用启动耗时过长而将其关闭。模拟器速度太慢，应用本身大概率没有问题。")
        put("Could not reach Legacy Store. Check your connection, and that Un1box is allowed network access in Android's app settings.", "无法连接经典商店。请检查网络，并确认 Android 应用设置中允许 Un1box 联网。")
        put("Stereo sound, as through the headphone jack; off plays through the device's speaker. Applies the next time the device starts", "立体声，如同通过耳机插孔输出；关闭则从设备扬声器播放。下次启动设备时生效")
        put("There is no crash report, so the app didn't crash. Either iOS closed it (for example, to free memory) or it is still running.", "没有崩溃报告，说明应用并没有崩溃。可能是 iOS 关闭了它（例如为释放内存），或者它仍在运行。")
        put("Records each frame's guest CPU, GL and lock time so the overlay and inspector can explain slow frames. Costs about 6% speed while on.", "记录每帧的客户机 CPU、GL 与锁时间，让浮层和检查器能解释慢帧。开启时约损失 6% 速度。")
        put("iOS pauses while Un1box is in the background, unless apps are installing. On, it carries on when you come back; off, it waits for you to tap Resume", "Un1box 进入后台时 iOS 会暂停（正在安装应用除外）。开启则回来后自动继续；关闭则等你点「继续」")
        put("On a Mac, Light Touch for Mac's firmwarekit tool prepares it and packs it into an .itbase folder. Copy that folder to this phone, then choose it here:", "在 Mac 上，用 Light Touch for Mac 的 firmwarekit 工具准备并打包成 .itbase 文件夹。把这个文件夹复制到手机，然后在这里选择它：")
        put("ANGLE uses the bundled graphics translation layer instead of Android's GPU driver. It may improve or reduce speed; applies on the next fresh guest start.", "ANGLE 使用内置的图形转换层代替 Android 的 GPU 驱动，可能提速也可能降速；下次全新启动客户机时生效。")
        put("For a device prepared elsewhere, such as on a computer: its iOS system (.itbase) and, for the iPod touch 1G and 2G and the original iPhone, its boot file.", "用于在别处（例如电脑上）准备好的设备：它的 iOS 系统（.itbase），以及 iPod touch 1G/2G 和初代 iPhone 需要的启动文件。")
        put("Skips large emulator-code cleanups after app changes. Uses more RAM and may slightly lower steady-state game speed; applies after restarting the emulated iPhone.", "应用切换后跳过大规模的模拟器代码清理。更占内存，稳态游戏速度可能略降；重启模拟设备后生效。")
        put("Turn on Detailed frame timing in Settings › Diagnostics to see where each frame's time went: guest CPU, GL and lock waits. It costs a few percent of speed while on.", "在设置 › 诊断中开启「详细帧计时」，即可看到每帧时间去向：客户机 CPU、GL 与锁等待。开启时会损失几个百分点的速度。")
        put("The frame copy into the guest's surface runs on a worker thread. Off copies it on the CPU instead, which can be steadier when the guest is waiting most of its frame anyway. Applies at the next guest start.", "把帧复制到客户机表面由工作线程完成。关闭则改在 CPU 上复制，当客户机本来大半帧都在等待时可能更稳。下次启动客户机时生效。")
        put("The emulator's log shows glMapBuffer had no device memory to hand back: the app created the buffer with a NULL data pointer, so the emulator returned 0. That is a gap in Un1box's graphics support, not an app-version mismatch.", "模拟器日志显示 glMapBuffer 没有设备内存可分配：应用创建缓冲区时传了 NULL 数据指针，模拟器因此返回 0。这是 Un1box 图形支持上的缺口，不是应用版本不匹配。")
        put("This phone's microphone becomes the device's input, for Voice Memos, Shazam and anything else that records; iOS asks inside the device the first time an app records. Android asks for the microphone permission when you turn this on. Applies the next time the device starts", "把本机麦克风作为设备的输入，供语音备忘录、Shazam 等录音应用使用；应用首次录音时 iOS 会在设备内询问。打开此选项时 Android 会请求麦克风权限。下次启动设备时生效")
        put("The frame block models a transfer's full hardware span (3 ms for a full screen), which the guest waits for as emulated time. Off ends a transfer when its pixels are written, which takes that span out of every frame the display pipeline shows. Applies at the next guest start.", "帧块按传输的完整硬件耗时建模（满屏约 3 毫秒），客户机以模拟时间等待这段耗时。关闭则在像素写完时结束传输，从而把这段耗时从显示管线呈现的每一帧中移除。下次启动客户机时生效。")
        put("With headphones, iOS plays games and music in stereo, unchanged.\n\nOn the speaker, iOS reshapes the sound for the device's small built-in speaker: mono, with the bass cut and a limiter to protect it. That processing runs on the emulated CPU, so games run a little slower, and the sound is closer to an old iPod's speaker than to your phone's.\n\nHeadphones also turn off the speaker's protection limiter. Some games count on it to keep loud sounds in check and can sound distorted without it; if a game crackles or clips, switch this off and restart the device.", "插入耳机时，iOS 以立体声播放游戏和音乐，不做任何处理。\n\n用扬声器时，iOS 会为设备小巧的内置喇叭重新塑形声音：单声道、削减低频，并加上限幅器保护喇叭。这段处理跑在模拟 CPU 上，所以游戏会稍慢；声音也更接近老 iPod 的喇叭，而不是你手机的。\n\n耳机还会关掉扬声器的保护限幅器。有些游戏靠它压制过响的声音，关掉后可能听起来失真；若某款游戏有爆音或削波，就关掉这个选项并重启设备。")
    }

    /** The English match, then the Chinese replacement, with $1.. for its groups. */
    private val rules: List<Pair<Regex, String>> = listOf(
        Regex("^Preparing runs on this phone, takes (.+) and needs about (.+) free\\. Un1box's guest tools are added for you\\.\$") to "准备过程在本机进行，耗时 \$1，需要约 \$2 可用空间。Un1box 会自动加入客户机工具。",
        Regex("^The app needs (.+), which (.+) doesn't have\\. It was built for a newer iOS than (.+)\\.\$") to "应用需要 \$1，但 \$2 中没有它。该应用是为比 \$3 更新的 iOS 构建的。",
        Regex("^The app crashed inside (.+), part of iOS\\. The emulation is the likely cause, not the app\\.\$") to "应用崩溃于 \$1（iOS 的组成部分）。最可能的原因是模拟本身，而不是应用。",
        Regex("^The app crashed in its own code \\((.+)\\)\\. The emulation can still be the cause if the app relied on something Un1box doesn't support yet\\.\$") to "应用崩溃于它自己的代码（\$1）。如果应用依赖了 Un1box 尚未支持的功能，模拟仍可能是原因。",
        Regex("^The emulator's log shows the app called (.+) \\(id (\\d+)\\), a graphics function Un1box doesn't support yet, and got 0 back\\. If the app crashed right after, that is the likely cause\\.\$") to "模拟器日志显示应用调用了 \$1（id \$2），这是 Un1box 尚未支持的图形函数，返回值为 0。如果应用随后立即崩溃，这很可能就是原因。",
        Regex("^Found (.+) in (.+)\$") to "在 \$2 中找到 \$1",
        Regex("^(\\S+) s selected\$") to "已选择 \$1 秒",
        Regex("^firmwarekit (.+) failed \\(exit (\\d+)\\)\$") to "firmwarekit \$1 失败（退出码 \$2）",
        Regex("^Downloading (.+) · (\\d+) of (\\d+)\$") to "正在下载 \$1 · 第 \$2 个，共 \$3 个",
        Regex("^Installing (.+) · (\\d+) of (\\d+)\$") to "正在安装 \$1 · 第 \$2 个，共 \$3 个",
        Regex("^Downloading (.+) of (.+)\$") to "正在下载 \$1，共 \$2",
        Regex("^Released (.+) · build (.+)\$") to "发布于 \$1 · 构建 \$2",
        Regex("^(.+) is truncated\$") to "\$1 已被截断",
        Regex("^(.+) is not a guest package\$") to "\$1 不是客户机软件包",
        Regex("^(.+) exceeds the guest package limit\$") to "\$1 超出客户机软件包上限",
        Regex("^(.+) requires a fresh unused worker\$") to "\$1需要新的、未使用过的工作线程",
        Regex("^Camera: (.+); trying again\$") to "相机：\$1；正在重试",
        Regex("^the camera reported error (.+)\$") to "相机报告错误 \$1",
        Regex("^touch rejected: (.+)\$") to "触摸被拒绝：\$1",
        Regex("^gesture cancelled: (.+)\$") to "手势已取消：\$1",
        Regex("^(.+); input release failed: (.+)\$") to "\$1；输入释放失败：\$2",
        Regex("^Guest audio output stopped \\(code (.+)\\)\\. Stop and restart if it does not recover\\.\$") to "客户机音频输出已停止（代码 \$1）。若无法自行恢复，请停止后重新启动。",
        Regex("^phone: (.+) frames · (.+) · (.*)\$") to "手机：\$1 帧 · \$2 · \$3",
        Regex("^Microphone read failed \\((.+)\\)\$") to "麦克风读取失败（\$1）",
        Regex("^The app crashed in (.+), called from (.+), using something that didn't exist\\. On a real device that is a bug in the app; here it can also mean the emulated iOS didn't provide something the app expected\\. The system log below may say which\\.\$") to "应用在 \$1 中崩溃，调用来自 \$2，用到了并不存在的东西。在真机上这是应用的 bug；在这里也可能意味着模拟的 iOS 没有提供应用期望的东西。下面的系统日志可能会说明是哪一种。",
        Regex("^Reusing the one from (.+)\$") to "复用来自 \$1 的那一份",
        Regex("^Last (.+)\$") to "最近 \$1",
        Regex("^(Tested|Tested, slow|Experimental|Untested|Doesn't start yet): (.+)\$") to "\$1：\$2",
        Regex("^Guest start failed: (.+)\\. Writable state and private diagnostics retained\\.\$") to "启动客户机失败：\$1。可写状态与私有诊断信息已保留。",
        Regex("^Couldn't record the system log \\((.+)\\)\\.") to "记录系统日志失败（\$1）。",
        Regex("^(.+) \\((.+)\\)\\. It is the same for every iOS version\\.\$") to "\$1（\$2）。各 iOS 版本都相同。",
        Regex("^(.+) graphics provider unavailable: (.+)\\. Restart Un1box to reset the graphics driver\\.\$") to "\$1 图形提供者不可用：\$2。重启 Un1box 以重置图形驱动。",
        Regex("^Download failed: (.+)\$") to "下载失败：\$1",
        Regex("^build (.+)\$") to "构建 \$1",
        Regex("^(\\d+) version\$") to "\$1 个版本",
        Regex("^Stop (.+)\\?\$") to "停止 \$1？",
        Regex("^Delete (.+)\\?\$") to "删除 \$1？",
        Regex("^Options for (.+)\$") to "\$1 的选项",
        Regex("^Added (.+); stop the running device to switch to it\$") to "已添加 \$1；停止正在运行的设备后可切换过去",
        Regex("^Added the device, but could not name it \\\"(.+)\\\": (.+)\$") to "已添加设备，但无法命名为「\$1」：\$2",
        Regex("^Added the device, but could not add Un1box's guest tools: (.+)\$") to "已添加设备，但无法加入 Un1box 的客户机工具：\$1",
        Regex("^Added the device, but could not finish setting it up: (.+)\$") to "已添加设备，但未能完成配置：\$1",
        Regex("^Installed (.+) apps\$") to "已安装 \$1 个应用",
        Regex("^Couldn't install (.+) apps\$") to "\$1 个应用安装失败",
        Regex("^(.+) installed, (.+) failed\$") to "已安装 \$1 个，\$2 个失败",
        Regex("^Installed (.+)\$") to "已安装 \$1",
        Regex("^Couldn't install (.+)\$") to "无法安装 \$1",
        Regex("^ · (.+) of (.+)\$") to " · 第 \$1 个，共 \$2 个",
        Regex("^Installing (.+)\$") to "正在安装 \$1",
        Regex("^Downloading iOS (.+)\$") to "正在下载 iOS \$1",
        Regex("^Downloading from (.+)\$") to "正在从 \$1 下载",
        Regex("^Downloading (.+)\$") to "正在下载 \$1",
        Regex("^Downloaded · (.+)\$") to "已下载 · \$1",
        Regex("^Unpacking · (.+)\$") to "正在解包 · \$1",
        Regex("^Copying (.+)\$") to "正在复制 \$1",
        Regex("^Adding (.+)\$") to "正在添加 \$1",
        Regex("^Preparing (.+)\$") to "正在准备 \$1",
        Regex("^(.+) is ready\$") to "\$1 已就绪",
        Regex("^(.+) is prepared\$") to "\$1 已准备完成",
        Regex("^Renamed to (.+)\$") to "已重命名为 \$1",
        Regex("^Imported (.+)\$") to "已导入 \$1",
        Regex("^(.+) s\$") to "\$1 秒",
        Regex("^Profiling (.+)\$") to "正在分析 \$1",
        Regex("^Saved profile: (.+)\$") to "已保存性能数据：\$1",
        Regex("^Choose iOS for (.+)\$") to "为 \$1 选择 iOS",
        Regex("^Tested: (.+)\$") to "已验证：\$1",
        Regex("^ · (.+) more to try\$") to " · 还有 \$1 个可尝试",
        Regex("^iOS (.+) to (.+)\$") to "iOS \$1 至 \$2",
        Regex("^Show (.+) beta versions\$") to "显示 \$1 个测试版",
        Regex("^(.+), iOS (.+)\$") to "\$1，iOS \$2",
        Regex("^Boot file · (.+) boot ROM\$") to "启动文件 · \$1 启动 ROM",
        Regex("^Required · the (.+) boot ROM\$") to "必需 · \$1 启动 ROM",
        Regex("^That file is not the (.+) boot ROM\$") to "该文件不是 \$1 启动 ROM",
        Regex("^That file is not the iOS (.+) IPSW for the (.+)\$") to "该文件不是用于 \$2 的 iOS \$1 IPSW",
        Regex("^None needed\\. The (.+) starts from the files preparation makes\\.\$") to "无需启动文件。\$1 由准备生成的文件直接启动。",
        Regex("^Downloads come from (.+), and must match the (.+) boot ROM exactly\\.\$") to "下载来自 \$1，且必须与 \$2 启动 ROM 完全一致。",
        Regex("^The download from (.+) did not match\$") to "来自 \$1 的下载内容不匹配",
        Regex("^No download is known for iOS (.+); choose an IPSW you have instead\$") to "iOS \$1 没有已知下载源；请改用手上的 IPSW",
        Regex("^Could not download iOS (.+) \\((.+)\\)\$") to "无法下载 iOS \$1（\$2）",
        Regex("^Could not download the boot file: (.+)\$") to "无法下载启动文件：\$1",
        Regex("^Looking up (.+)\$") to "正在查询 \$1",
        Regex("^This takes (.+)\\. You can leave Un1box; preparation continues in the background\\.\$") to "约需\$1。你可以离开 Un1box，准备会在后台继续。",
        Regex("^Released (.+)\$") to "发布于 \$1",
        Regex("^It could not be added as a device: (.+)\$") to "无法将其作为设备添加：\$1",
        Regex("^Frame times · (.+) frames\$") to "帧时间 · \$1 帧",
        Regex("^Frame (\\d+)\$") to "第 \$1 帧",
        Regex("^Show all (\\d+)\$") to "显示全部 \$1 条",
        Regex("^at (.+)\$") to "在 \$1 处",
        Regex("^vCPU (.+)% of wall\$") to "vCPU 占实际时间 \$1%",
        Regex("^Serial (.+)\$") to "序列号 \$1",
        Regex("^Version (.+) is installed\\. An older version may not read its data\\.\$") to "已安装 \$1 版本。更旧的版本可能读不了它的数据。",
        Regex("^Version (.+) is available\$") to "\$1 版本可用",
        Regex("^Version (.+)\$") to "版本 \$1",
        Regex("^(.+) versions\$") to "\$1 个版本",
        Regex("^(.+) apps\$") to "\$1 个应用",
        Regex("^Licenses could not be loaded: (.+)\$") to "许可证加载失败：\$1",
        Regex("^License could not be read: (.+)\$") to "许可证读取失败：\$1",
        Regex("^Source page could not be opened: (.+)\$") to "无法打开源码页面：\$1",
        Regex("^Performance overlay: (.+)\$") to "性能浮层：\$1",
        Regex("^The app needs (.+), which this iOS doesn't include\\.\$") to "该应用需要 \$1，而此 iOS 不包含它。",
        Regex("^Thread (.+):\$") to "线程 \$1：",
        Regex("^Guest app service exited \\((.+)\\) without a result\\. (.+)\$") to "客户机应用服务退出（\$1），没有返回结果。\$2",
        Regex("^Legacy Store could not answer the request \\(HTTP (.+)\\)\\.\$") to "经典商店未能响应该请求（HTTP \$1）。",
        Regex("^Legacy Store sent an unreadable (.+)\\.\$") to "经典商店返回了无法解析的\$1。",
        Regex("^Start (.+) from its saved data after an unclean shutdown, keeping diagnostics\$") to "非正常关机后，从已保存的数据启动 \$1，并保留诊断信息",
        Regex("^Install a newer \\.itpack into (.+); it applies at the next start\$") to "向 \$1 安装更新的 .itpack，下次启动时生效",
        Regex("^Requires iOS (.+); this device runs iOS (.+)\\.\$") to "需要 iOS \$1；此设备运行 iOS \$2。",
        Regex("^This app requires iOS (.+); the guest runs (.+)\\.\$") to "该应用需要 iOS \$1；客户机运行 \$2。",
        Regex("^Requires iOS (.+)\$") to "需要 iOS \$1",
        Regex("^iOS (.+) or later\$") to "iOS \$1 或更高版本",
        Regex("^The app executable requires iOS (.+)\$") to "应用可执行文件要求 iOS \$1",
        Regex("^The IPA member (.+) is truncated\\.\$") to "IPA 成员 \$1 被截断。",
        Regex("^The IPA contains an unsupported or oversized member: (.+)\$") to "IPA 含有不受支持或过大的成员：\$1",
        Regex("^The IPA is incomplete or damaged: (.+)\$") to "IPA 不完整或已损坏：\$1",
        Regex("^Guest tools updated for (.+)\$") to "已为 \$1 更新客户机工具",
        Regex("^Guest start failed: (.+)\$") to "启动客户机失败：\$1",
        Regex("^Guest worker unavailable: (.+)\\. Writable state retained; Start creates a fresh worker\\.\$") to "客户机工作进程不可用：\$1。可写状态已保留；点「开始」会新建工作进程。",
        Regex("^Stop admission failed; writable state retained: (.+)\$") to "停止请求失败；可写状态已保留：\$1",
        Regex("^Couldn't load versions: (.+)\$") to "版本加载失败：\$1",
        Regex("^Couldn't read the apps on this device: (.+)\$") to "无法读取此设备上的应用：\$1",
        Regex("^Couldn't read crash reports: (.+)\$") to "无法读取崩溃报告：\$1",
        Regex("^Couldn't check the Legacy Store: (.+)\$") to "无法查询经典商店：\$1",
        Regex("^Couldn't check for newer versions\\. (.+)\$") to "无法检查新版本。\$1",
        Regex("^Could not connect to Legacy Store: (.+)\$") to "无法连接经典商店：\$1",
        Regex("^Nothing found in (.+)\$") to "在 \$1 中没有找到任何内容",
        Regex("^No prepared iOS system in (.+)\$") to "\$1 中没有已准备好的 iOS 系统",
        Regex("^Cannot read (.+)\$") to "无法读取 \$1",
        Regex("^Cannot create (.+)\$") to "无法创建 \$1",
        Regex("^Guest app services: (.+)\$") to "客户机应用服务：\$1",
        Regex("^Guest tools not updated: (.+)\$") to "客户机工具未更新：\$1",
        Regex("^Firmware worker unavailable: (.+)\$") to "固件工作进程不可用：\$1",
        Regex("^Device library unavailable: (.+)\$") to "设备库不可用：\$1",
        Regex("^Could not save: (.+)\$") to "无法保存：\$1",
        Regex("^The emulator's log shows it refused (.+), which may have caused the crash\\.\$") to "模拟器日志显示它拒绝了 \$1，这可能导致了崩溃。",
    )

    private val months = listOf("January", "February", "March", "April", "May", "June",
        "July", "August", "September", "October", "November", "December")

    /** "March 2010" is a release date in the firmware list; write it the Chinese way. */
    private val monthYear = Regex("([A-Z][a-z]+) (\\d{4})")

    fun translate(text: String): String = translate(text, 0)

    private fun translate(text: String, depth: Int): String {
        exact[text]?.let { return localizeDate(it) }
        if (depth < 2) for ((pattern, replacement) in rules)
            if (pattern.containsMatchIn(text)) return localizeDate(expand(pattern, replacement, text, depth))
        return localizeDate(text)
    }

    /** The replacement with its $1.. filled in, each filled part translated in turn. */
    private fun expand(pattern: Regex, replacement: String, text: String, depth: Int): String {
        val match = pattern.find(text) ?: return text
        val out = StringBuilder()
        var i = 0
        while (i < replacement.length) {
            val c = replacement[i]
            if (c == '\\' && i + 1 < replacement.length) { out.append(replacement[i + 1]); i += 2; continue }
            if (c == '$' && i + 1 < replacement.length && replacement[i + 1].isDigit()) {
                val group = match.groupValues.getOrElse(replacement[i + 1] - '0') { "" }
                out.append(translate(group, depth + 1))
                i += 2; continue
            }
            out.append(c); i += 1
        }
        return out.toString()
    }

    private fun localizeDate(text: String): String {
        val match = monthYear.matchEntire(text) ?: return text
        val month = months.indexOf(match.groupValues[1])
        if (month < 0) return text
        return "${match.groupValues[2]} 年 ${month + 1} 月"
    }
}
