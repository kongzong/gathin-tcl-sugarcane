# gathin for TCL / 雷鸟 sugarcane（Android 8.0 / SDK 26 / MStar）

本仓库是 [CleanYourTV（gathin）](https://github.com/Gathin23/cleanyourtv) 启动器在 **TCL 雷鸟 sugarcane** 固件（Android 8.0 / API 26 / MStar 平台）上的**设备专属适配版**，MIT 许可证，原作者版权保留。

> 它不是一个通用 fork，而是「在官方 gathin 源码基础上、叠加 sugarcane 固件需要的几处改动」的集合。大多数代码来自上游，本文只记录 TCL 相关的差异与踩坑。

## 设备与目标

- 设备：TCL 雷鸟 sugarcane（中国版），Android 8.0 / SDK 26 / MStar。
- 目标：用 gathin 取代原生桌面，接管 Home 键与几个被固件绑到购物/推荐类 app 的遥控器按键。
- 调试入口：`adb connect <TV_IP>:5555`（无线 adb，端口 5555）。`<TV_IP>` 是你电视在局域网里的地址。

## 相对上游的改动

1. **遥控器按键改绑**（见 `app/src/main/res/values/config.xml` 的 `app_keys`）：把固件强行绑到自家 app 的遥控器键，改去打开第三方 app。通过 `HomeKeyService` 全局按键过滤实现，任何 app / 原生桌面下均生效（前提：无障碍服务已启用且 `key_filter=true`）。
2. **壁纸主色取色**（`WallpaperPlayer.java` + `Config.java` 的 `wallpaper_accent`）：dock 高亮环与焦点环取当前壁纸主色，让桌面跟壁纸风格统一。
3. **dock 长条加深**（`DockDecoration.java` + `AppAdapter.java`）：把底部 dock 横条调成更深的暗色玻璃，和上方图标托盘做出区分。
4. **原生桌面「我的应用」入口**（`AndroidManifest.xml` 给 MainActivity 增加标准 `LAUNCHER` intent-filter）：冷启动停在原生桌面时，可在「我的应用」里找到 gathin 并打开，打开瞬间即接管 Home 与改绑键。

## 遥控器改绑表

| 原固件键 | Android scanCode | keyCode | 改绑到 |
|---|---|---|---|
| T惠购 | 227 | 4020 | `com.V2.blb5` |
| 同步院线 | 251 | 4503 | `com.fongmi.android.tv`（FongMi 影视） |
| 直播 | 377 | 170 | `com.jerry.livehd`（高清电视） |

`config.xml` 里每条都写了 `scan:<scanCode>:<pkg>` 与 `<keyCode>:<pkg>` 双通道，`scancode` 优先于 `keycode`，两者都写是为了稳妥。要改绑哪个键，按同样的格式加一行、重新编译安装即可。`<TV_IP>` 上抓键值的可靠办法：在 `HomeKeyService.onKeyEvent` 里临时 `Log.d` 打印 `getKeyCode()` / `getScanCode()`，用真遥控器按键后用 `adb logcat` 或 `run-as com.gathin.tvhome cat files/keyprobe.log` 读取（本机 logcat 在此固件上常静默，改用文件落地）。

> 注意：Linux 层 `getevent` 打出的 `EV_KEY` code（如 T惠购 在 Linux 是 226）**不等于** Android 的 `getScanCode()`（这里是 227）。以 Android 层为准。

## 构建与安装

仓库根目录的 `build-and-install.sh`（Linux/macOS）或 `build-and-install.ps1`（Windows）会编译并安装：

```bash
# Linux / macOS
TV_HOST=<TV_IP> ./build-and-install.sh
# Windows (PowerShell)
.\build-and-install.ps1 -TvHost <TV_IP>
```

构建需要本机 Android SDK（JDK 17、Gradle 9.x）。`local.properties` 里的 `sdk.dir` 是机器相关路径，已加入 `.gitignore`，请在自己机器上填。

## 冷启动与服务恢复（重要坑）

- 该 TCL 固件**从不给第三方 app 发 `BOOT_COMPLETED`**，所以开机时 `BootService` 的恢复链不会触发。
- 无障碍服务（HomeKeyService / InputKeyService）的恢复**唯一入口**在 `MainActivity`：打开 gathin 桌面时若发现服务未启用就自动 `repair()`。也就是说，**冷启动后、手动打开 gathin 之前，Home 键与改绑键都不生效**，打开 gathin 瞬间才接管。
- `adb install -r` 会杀掉 gathin 进程，导致 HomeKeyService 解绑（`enabled_accessibility_services` 里还在、但实际没绑定）。装完需要重绑：
  ```bash
  adb -s <TV_IP>:5555 shell "am broadcast -a com.gathin.tvhome.DEBUG_REPAIR"
  ```
- 冷启动时固件还会短暂清空无障碍列表，服务有数秒到约两分钟恢复窗口，期间按键仍会漏给固件。

## 精简系统应用（参考清单，按需禁用）

以下包名在本机上可安全 `pm disable-user` 或 `pm uninstall`（系统应用视固件而定；部分 TCL app 会被固件自动恢复，禁用仅作双保险）。**仅作参考，操作前请确认自己设备的包名**：

批次 1 · 遥测 / 广告 / 诊断：
`com.tcl.bootadservice` `com.tcl.c2dm.client` `com.android.tcl.messagebox` `com.tcl.bi` `com.tcl.usagestats` `com.tcl.tvhealthcheck` `com.tcl.xian.StartandroidService`

批次 2 · 自带内容 / 推荐位 / 商店：
`com.tcl.vod` `com.tcl.tliveplay` `com.tcl.waterfall.resource` `com.tcl.wholenetsearch` `com.tcl.tshop` `com.tcl.ffeducation` `com.tcl.ffkaraoke` `com.tcl.playskill` `com.tcl.browser` `com.tcl.weixin` `com.tencent.qqmusictv` `jd.video.basecomponent`

批次 3 · 语音助手（本机选择不保留）：
`com.iflytek.xiri` `com.iflytek.xiri.recorder.svc` `com.iflytek.xiri2.hal` `com.iflytek.xiri.translate` `com.iflytek.mictest` `com.tcl.micmanager` `com.tcl.walleve` `com.tcl.walleve.aidaemon` `com.tcl.recognize.service`

批次 4 · OTA / 应用商店：
`com.tcl.versionUpdateApp` `com.snm.upgrade` `com.tcl.appmarket2`

## 许可证

本仓库基于 gathin，遵循其 **MIT 许可证**（见 `LICENSE`）。其中 `app/src/main/res/font/outfit.ttf` 字体为 SIL OFL 1.1，见 `FONT-LICENSE-OFL.txt`。

上游：https://github.com/Gathin23/cleanyourtv
