# AutoMerge — 自动影音合成 1.3

Android 8.0+，ARM64 / ARMv7。首次选择视频、音频和输出文件夹，然后点击“开始自动合成”。文件夹授权会保存，以后只需点击开始。纯本地运行，不需要网络权限。

## 配对与合成

- 扫描所有子文件夹，跳过 .pending- 未完成录制和 .trashed- 已删除文件，按去除扩展名后的相同文件名配对，不区分英文大小写。
- 优先使用相同相对目录中的同名音频，否则只接受整个音频目录中唯一的同名音频。重名或缺少音频时跳过并显示原因。
- 示例：视频/2026-10-01/clip.mov + 音频/2026-10-01/clip.wav → 输出/2026-10-01/clip__merged.mkv。
- 输出为 MKV，复制第一个视频流和外部文件的第一个音频流，不重新编码，不保留原视频的音轨。默认保留原文件。
- 已有同名输出会跳过。处理中使用带 `.merging-` 的临时名称，只有 FFmpeg 成功后才重命名。取消和失败会删除本次临时输出。
- 文件名配对不等于自动对齐波形；视频和音频需要相同录制起点。保留各自完整时长，不使用 `-shortest` 裁剪。
- 允许锁屏继续处理，但手机厂商的省电策略仍可能终止应用。若被系统强行终止，可能留下 `.merging-` 临时文件，可以手动删除后再次开始，已完成输出会跳过。
- 建议选择手机内部存储或 SD 卡本地文件夹。部分云盘文档提供器不支持 FFmpeg 的随机读写和重命名。
- Android 文件选择器可能不能直接授权 Download 根目录，请在里面建立“合成输出”子文件夹。
- 这是点击启动的批量工具，未实现长期监控新文件或定时执行。

## 合成成功后删除原文件（1.2 新增）

界面中有默认关闭的开关，选择会保存，合成期间不能更改本次选项。开启后，仅对本次新生成、FFmpeg 成功结束、重命名保存成功、FFprobe 重新读取确认同时含视频和音频流的输出安排原文件清理。清理在本批合成结束后进行。开关关闭、失败、校验不通过、跳过已有结果或在清理前取消批次时，原文件保留。

只删除配对的原视频和原音频，不删除文件夹或输出。若同一音频被多个视频使用，必须全部对应输出都在本次完成并通过校验，才删除该音频一次。系统不允许删除或没有写入授权时，输出仍保留，并记录删除失败原因。清理过程中点击停止，会停止剩余删除，已删除文件无法撤销。

新增的六项检查覆盖开关关闭、批次取消、无合格输出、部分成功、共享音频仍被未完成配对使用，以及共享音频全部完成后去重删除。手机文档提供器的真实删除仍需在设备上验证。

## FFmpeg 参数

```text
-hide_banner -nostdin -y -i VIDEO -i AUDIO -map 0:v:0 -map 1:a:0 -c:v copy -c:a copy -f matroska OUTPUT
```

路径通过 FFmpegKit SAF 协议传入，命令使用参数数组，文件名中的空格和引号不会被解释成命令。

## 构建

源码使用 Android Java 原生界面，无 Gradle 依赖。`build.py` 将编译源码，运行配对规则测试，使用 D8 生成 Dex，打包两个 ARM 架构，zipalign 后生成并验证签名 APK。

所需工具放在自选 tools 目录：

| 文件/目录 | 获取地址 |
|---|---|
| jdk/ 解压后的 JDK 17 | https://aka.ms/download-jdk/microsoft-jdk-17.0.18-windows-x64.zip |
| platform/ 解压后的 Android API 35 | https://dl.google.com/android/repository/platform-35_r02.zip |
| buildtools/ 解压后的 Build Tools 35.0.1 | https://dl.google.com/android/repository/build-tools_r35.0.1_windows.zip |
| ffmpeg.aar | https://repo.maven.apache.org/maven2/io/github/jamaismagic/ffmpeg/ffmpeg-kit-main-min-16kb/6.1.4/ffmpeg-kit-main-min-16kb-6.1.4.aar |
| smart.jar | https://repo.maven.apache.org/maven2/com/arthenica/smart-exception-java/0.2.1/smart-exception-java-0.2.1.jar |
| smart-common.jar（必须同时打包） | https://repo.maven.apache.org/maven2/com/arthenica/smart-exception-common/0.2.1/smart-exception-common-0.2.1.jar |

```powershell
python build.py --tools C:\path\to\tools --out C:\path\to\AutoMerge.apk
```

每个构建目录第一次构建会创建用于本地分发的签名密钥。后续更新请保留相同密钥，否则 Android 无法覆盖安装。源码包不包含本次构建私钥。

## 验证范围

1.1 修复了遗漏 smart-exception-common 导致合成引擎初始化失败的问题。引擎检查在扫描和创建输出之前执行，失败时显示异常类型、堆栈和底层原因。

构建验证所有第三方 Java 类引用的依赖完整性。回归测试先移除公共库，确认能复现 NoClassDefFoundError；再加载完整依赖，检查 FFmpegKit 初始化所调用的注册操作和异常格式化。另直接解析生成的 Dex，确认初始化必需的类存在于 class definitions 中。

配对测试覆盖同日期匹配、相对目录优先、唯一名称回退、大小写、重复音频、缺少音频、排除生成结果和临时文件。编译验证 Java/API 与依赖兼容性，签名验证安装包完整性。1.1 的合成功能已有使用者在本对话中反馈可用。1.3 更换了底层合成库，仍需重新在真机上验证合成、播放、同步和删除。当前发布标为测试版。

许可证和依赖见 LICENSE、THIRD_PARTY.md。

## 1.3 发布修订

替换了无法确认精确构建源码的旧依赖。新库使用公开 CI 发布的 min 6.1.4，发布标签、CI head commit 和附件 SHA-256 相互对应。库不启用 GPL 编解码器组件；应用仍维持 GPL-3.0-or-later。对应完整源码归档随仓库和 source.zip 提供。详细证据及原生库构建步骤见 NATIVE_BUILD.md。应用构建前会拒绝哈希不符的依赖。

## 下载和反馈

项目首页：https://github.com/LuoTianyi007/AutoMerge

测试版下载：https://github.com/LuoTianyi007/AutoMerge/releases/tag/v1.3

请在发布页 Assets 中下载 APK。AutoMerge-1.3-source.zip 包含应用源码、第三方源码归档、许可证和构建证据。不要公开上传本地签名密钥；更新使用相同密钥。
