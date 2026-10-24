# TCL 雷鸟 TV：debloat + 自建桌面（gathin）

> 最后更新：2026-10-24。前半是 debloat 与桌面演进，后半是当前 gathin 桌面的完整定制与排障。

## 设备

| 项 | 值 |
|---|---|
| 品牌 / 型号 | TCL / `sugarcane`（雷鸟中国版） |
| 系统 | Android 8.0.0（SDK 26），MStar 平台，构建 2021-11-03 |
| 内存 | 1.86 GB（可用约 760 MB） |
| 网络 | 有线 `10.200.1.97` + Wi-Fi `10.200.1.136`（同一台，双网卡） |
| ADB | `10.200.1.97:5555`（IP 固定 .97）；`persist.adb.tcp.port=5555` |

## 结论（当前状态）

| 项 | 结果 |
|---|---|
| Debloat | **成功**：禁用 32 个包，重启后全部保持，无开机复活 |
| 自建桌面 | **gathin 改造版**（`com.gathin.tvhome`），非原厂桌面 |
| Home 键接管 | 无障碍按键过滤（固件拒装声明 `category.HOME` 的包，见下） |
| 冷启动后 | **需手动点一次 gathin 图标**，之后 Home 键、ADB、三个改绑键全部归位 |
| 遥控器改绑 | 3 个厂商键已改绑（T惠购→blb5、同步院线→影视、直播→高清电视） |
| 私人 DNS 广告拦截 | 已开（AdGuard） |
| 开机广告 | 未处理（TCL 客服电话可关，用户自行处理） |

## 桌面演进

| 阶段 | 方案 | 结局 |
|---|---|---|
| 第一轮 | 归藏电视改造版（`io.github.op7418.guizangtv`） | 曾接管 Home，后**用户拍板弃用**，恢复 gathin |
| 第二轮 | **gathin 改造版**（`com.gathin.tvhome`） | **当前在用**，本文档后半全部围绕它 |

源码：gathin 在 `repo/launcher/`；guizang 改造记录留在 `guizang/` 备查。

## 共同基础：TCL 签名闸门

固件拒装任何声明 `android.intent.category.HOME` 的包：

```
INSTALL_PARSE_FAILED_INCONSISTENT_CERTIFICATES: disable install launcher app not signature by tcl
```

同一台机器装普通 APK 是 `Success`，闸门只认 HOME 这一个标记。绕法是**不声明 HOME**，
用无障碍服务的按键过滤接管 Home 键。`category.LAUNCHER` 不受影响，可正常加
（gathin 已补，冷启动后能从原厂桌面「我的应用」点进来）。

---

# 当前桌面：gathin 定制

## 外观改造

| 项 | 状态 | 说明 |
|---|---|---|
| 中文系统字体 | ✅ | `values-zh/fonts.xml` 走系统 sans-serif，去掉 Outfit 细字重（Outfit 无中文字形） |
| 图标统一 | ✅ | `icon_only_apps=true`，全部纯图标 84dp，隐藏贴图文字；选中态名称只在下方显示一次 |
| 壁纸 | ✅ | 5 张 Unsplash 图打包进 `assets/`，`ThemeBootstrap` 首启播种，30 分钟轮播、压暗 18% |
| 手动换壁纸 | ✅ | 长按 OK → "Change wallpaper"，或 `am broadcast -a com.gathin.tvhome.action.WALLPAPER_NEXT` |
| dock 带子 | ✅ | `dock_glass=true`，深色玻璃（黑 40%→22%）+ 壁纸主色描边 |
| 图标托盘 | ✅ | 磨砂玻璃（`tile_bg.xml`），与 dock 带 / 顶部 pill 同套质感 |
| 聚焦环 | ✅ | 跟随当前壁纸主色，换壁纸实时变色 |
| LAUNCHER 入口 | ✅ | 冷启动后可从原生桌面「我的应用」进 gathin |

**壁纸主色取色**：`WallpaperPlayer.decode()` 在压暗前对原图缩至 48px，量化 4096 桶取最高频
（滤暗部 / 过曝），提亮饱和后回调；`DockDecoration` 与 `AppAdapter` 据此染色。

## 遥控器改绑

配置在 `repo/launcher/app/src/main/res/values/config.xml` 的 `app_keys`，由 `HomeKeyService`
（无障碍按键过滤）**全局**拦截——原生桌面、其他 app、gathin 里都生效。

| 遥控器按键 | keyCode / scanCode | 打开 |
|---|---|---|
| 原「T惠购」键 | 4020 / 227 | `com.V2.blb5` |
| 「同步院线」 | 4503 / 251 | `com.fongmi.android.tv`（影视） |
| 「直播」 | 170 / 377 | `com.jerry.livehd`（高清电视） |

每条同时配 `scan:<scancode>` 和 `<keycode>` 两条（双通道保险）。

**T惠购本体** `com.tcl.tshop` 已 `pm disable-user` 冻结，但 TCL 会不定期恢复；
键已被接管，恢复也收不到键。

## 关键机制与坑（全部实测）

### 1. 服务恢复时机（最重要，曾记错已纠正）

- **该固件从不给第三方 app 发 `BOOT_COMPLETED`**（`BootService.java` 注释原文）。
  所以 BootService 的开机 retry 链（2s→120s）**永不触发**。
- 服务恢复唯一入口：`MainActivity` 里 `if (!BootService.isEnabled(this)) BootService.repair(this);`
  —— **每次打开 gathin 桌面时**才重写无障碍列表并重绑。
- **结论：冷启动后停在原生桌面时，Home 键 + 三个改绑键都不生效；
  必须手动打开一次 gathin 后才全部接管。**
- 已接受的现状：冷启动只发生在主动长按关机、系统更新、系统崩溃，日常不影响。

### 2. 装 APK 后服务不重连

`adb install -r` 会杀 gathin 进程 → HomeKeyService 留在 `enabled_accessibility_services`
列表但**没有绑定**（`dumpsys accessibility` 里看不到），`app_keys` 形同虚设。

**每次装完必须重绑**：

```bash
adb -s 10.200.1.97:5555 shell "am broadcast -a com.gathin.tvhome.DEBUG_REPAIR"
```

确认绑定成功的标志：`dumpsys accessibility | grep -i 'Home button'` 出现 `capabilities=8`。

### 3. 键值采集必须用 Android 层值

- `getevent -lt` 给的是 **Linux 层** EV_KEY code；`KeyEvent.getScanCode()/getKeyCode()`
  是 **Android 层**，两者可能不同（实测：T惠购键 Linux 226 → Android scanCode 227 / keyCode 4020）。
  按 Linux 值配 `scan:` 不生效。
- 本机 logcat 静默，可靠办法：`HomeKeyService.onKeyEvent` 临时写 `files/keyprobe.log`，
  用 `run-as com.gathin.tvhome cat files/keyprobe.log` 读。
- 流程：装探针版 → 用户真实按键 → 读日志拿 `key=` / `scan=` → 配 `app_keys` → 移除探针。

### 4. 应用包名辨识

电视应用 label 是资源引用，`dumpsys package` / `pm dump` 只能拿到 `labelRes=0x...`，
解析不出中文。可靠办法：让用户点开该 app 停住，读前台包名：

```bash
adb -s 10.200.1.97:5555 shell "dumpsys activity activities | grep -iE 'mResumedActivity'"
```

或让用户直接指认包名。

### 5. 构建环境

- wrapper 要 Gradle 8.9 但缓存里只有半截 `.part`，会卡在重下 150MB；
  直接用缓存里已解压的 `gradle-9.0.0`（与 AGP 8.5.2 实测兼容）。
- Java 不读 `https_proxy` 环境变量，必须用 `GRADLE_OPTS` 传 JVM 代理参数。
- 增量构建约 15-30 秒。

```bash
cd repo/launcher
export JAVA_HOME="/c/Program Files/Java/jdk-17"
export ANDROID_HOME="/c/Users/MR/AppData/Local/Android/Sdk"
export GRADLE_OPTS="-Dhttp.proxyHost=127.0.0.1 -Dhttp.proxyPort=5217 -Dhttps.proxyHost=127.0.0.1 -Dhttps.proxyPort=5217"
GR="/c/Users/MR/.gradle/wrapper/dists/gradle-9.0.0-all/5xsng250u137f3y00wn4jtnj6/gradle-9.0.0/bin/gradle"
"$GR" assembleDebug --console=plain --no-daemon
```

---

# Debloat（两个阶段共用，仍然有效）

## 已禁用（32 个，清单在 `debloat/`）

| 批次 | 类别 | 包 |
|---|---|---|
| 1 | 遥测 / 广告 | `com.tcl.bootadservice`、`com.tcl.c2dm.client`、`com.android.tcl.messagebox`、`com.tcl.bi`、`com.tcl.usagestats`、`com.tcl.tvhealthcheck`、`com.tcl.xian.StartandroidService` |
| 2 | TCL 内容 / 商店 / 预装流媒体 | `com.tcl.vod`、`com.tcl.tliveplay`、`com.tcl.waterfall.resource`、`com.tcl.wholenetsearch`、`com.tcl.tshop`、`com.tcl.ffeducation`、`com.tcl.ffkaraoke`、`com.tcl.playskill`、`com.tcl.browser`、`com.tcl.weixin`、`com.tencent.qqmusictv`、`jd.video.basecomponent` |
| 3 | 语音助手 | `com.iflytek.xiri`、`xiri.recorder.svc`、`xiri2.hal`、`xiri.translate`、`com.iflytek.mictest`、`com.tcl.micmanager`、`com.tcl.walleve`、`com.tcl.walleve.aidaemon`、`com.tcl.recognize.service` |
| 4 | OTA / TCL 应用商店 | `com.tcl.versionUpdateApp`、`com.snm.upgrade`、`com.tcl.appmarket2` |
| — | 探针 | `com.tcl.gamecenter` |

**没动你自己装的 App**：奇异果、酷喵、云视听、BBLL、blb5、Kodi、FongMi、MX Player、文件管理器等全部保留。

## 故意保留

`com.tcl.cyberui`（原厂桌面，仍是系统 Home）、`com.tcl.tv`（Live TV / DTV / HDMI）、
`com.gitv.dvb.live`、`com.tcl.settings`、投屏三件套 + `com.extscreen.runtime`、
`com.tcl.guard`（电视卫士，U 盘装 APK 的唯一通道）、`com.tcl.autopair`、搜狗输入法、
`com.tcl.shutdowndialog`、`com.tcl.providers.config`、`com.tcl.UpdatePeripheral`、全部 `com.android.*` 核心包。

## 广告拦截 DNS

```
settings put global private_dns_specifier dns.adguard-dns.com
settings put global private_dns_mode hostname
```

拦应用/网页广告，**拦不了 YouTube 广告**。撤销：`private_dns_mode opportunistic`。

## 已知做不了（硬件/固件限制，非改动造成）

- **HDMI 直切**：本机没有 `tv_input` 服务（无 TvInputManager），`com.tcl.sourcemananger`
  在 2020-09-18 固件更新里已被删除，`com.tcl.ShortcutKey` 广播发了没有接收方。
  HDMI 只能用遥控器的信号源键。
- **设置 → 信号源** 菜单项本来就是死的（同上）。

---

# 脚本与命令

## 脚本

| 脚本 | 作用 | 适用 |
|---|---|---|
| `scripts\tv.sh` | shot / key / home / disable / enable（bash，Git Bash 用） | 通用 |
| `scripts\reboot-probe.sh` | 重启自恢复实测（全程不碰电视，记录恢复时间点） | 通用 |
| `scripts\restore-all.ps1` | 恢复全部 32 个包 + DNS 回运营商默认 | 通用 |
| `scripts\build-guizang.ps1` | 编译安装武装归藏电视 | 已弃用（guizang 阶段） |
| `scripts\remove-guizang.ps1` | 解除 Home 接管并卸载归藏电视 | 已弃用 |
| `scripts\fix-home-items.ps1` | 复位归藏首页排序 | 已弃用 |

## 常用命令（gathin 阶段）

```bash
DEV=10.200.1.97:5555
adb connect $DEV

# 装 APK 后必做：重绑按键服务
adb -s $DEV shell "am broadcast -a com.gathin.tvhome.DEBUG_REPAIR"

# 服务绑定状态（应出现 capabilities=8）
adb -s $DEV shell "dumpsys accessibility | grep -i 'Home button'"

# 抓当前前台包名
adb -s $DEV shell "dumpsys activity activities | grep -iE 'mResumedActivity'"

# 冻结 / 解冻
adb -s $DEV shell "pm disable-user com.tcl.tshop"
adb -s $DEV shell "pm enable com.tcl.tshop"

# 读探针日志
adb -s $DEV shell "run-as com.gathin.tvhome cat files/keyprobe.log"

# 换壁纸
adb -s $DEV shell "am broadcast -a com.gathin.tvhome.action.WALLPAPER_NEXT"
```

---

# gathin 源码改动清单

位置：`repo/launcher/app/src/main/`

| 文件 | 改动 |
|---|---|
| `res/values-zh/fonts.xml` | 中文走系统 sans-serif |
| `res/values/config.xml` | `dock_glass=true`、`wallpaper_dim=18`、`app_keys` 三键改绑 |
| `res/values/bools.xml` | `icon_only_apps`、`wallpaper_accent` |
| `res/layout/item_app.xml` | 图标 84dp、隐藏贴图 label |
| `res/drawable/tile_bg.xml` | 磨砂玻璃 + 聚焦提亮 |
| `java/.../WallpaperPlayer.java` | 主色提取 + `next()` + `AccentListener` |
| `java/.../DockDecoration.java` | 深色玻璃带 + `setAccent()` |
| `java/.../AppAdapter.java` | 图标模式、动态聚焦环、`setAccent/setHost` |
| `java/.../ThemeBootstrap.java` | 首启从 assets 播种壁纸 |
| `java/.../MainActivity.java` | 播种接线、`onWallpaperAccent`、打开时自检重绑服务 |
| `java/.../BootService.java` | 无障碍恢复（repair）机制 |
| `java/.../HomeKeyService.java` | `app_keys` 全局拦截 |
| `AndroidManifest.xml` | 补 `category.LAUNCHER`、存储权限 |
| `build-and-install.sh` | 补 `WRITE_EXTERNAL_STORAGE` 的 pm grant |

---

# 手动测试清单（真人拿遥控器测）

先确认：电视已开调试、`adb connect 10.200.1.97:5555` 能连、APK 已编译。

| # | 测什么 | 怎么测 | 预期 |
|---|---|---|---|
| 1 | 服务绑定 | 装 APK 后跑 DEBUG_REPAIR，再 `dumpsys accessibility \| grep 'Home button'` | 出现 `capabilities=8` |
| 2 | 桌面外观 | 看首页 | 壁纸上屏、dock 深带、浅托盘、聚焦环随壁纸主色 |
| 3 | Home 接管 | 在任意 App 里按 Home | 进 gathin（不是原厂） |
| 4 | 原厂桌面入口 | 冷启动后进原厂「我的应用」 | 能找到 gathin 图标，点开即用 |
| 5 | 换壁纸 | 长按 OK → Change wallpaper，或发广播 | 壁纸切换，聚焦环/dock 描边变色 |
| 6 | 改绑键 ×3 | 依次按 T惠购键 / 同步院线 / 直播 | 分别打开 blb5 / 影视 / 高清电视 |
| 7 | 冷启动恢复 | 按电源键关机再开，点一次 gathin 图标 | Home 键 + 三键全部归位 |
| 8 | ADB 自恢复 | 冷启动后 `adb connect` | 能连（`persist.adb.tcp.port=5555` 生效） |

> 验证 Home 接管不要只看高亮：要看**行为**（按 Home 进了哪个 Activity）。

---

# 收尾状态

- 网络 ADB 仍开着。要关：电视上关调试开关 + 重启，或 `adb shell settings put global adb_enabled 0`。
- 截图在 `shots/`（早期阶段）与 `scripts/gw_stage/`（gathin 验收截图 `gw_*.png`）。
- 完整包列表 / 设置 / `dumpsys` 在 `backup/sugarcane-20261023/`。
- guizang 改造版已卸载，源码与改造记录留在 `guizang/` 备查。
- **遗留可选项**：① 冷启动头几秒按键仍漏给固件，可把 tshop 禁用加进 BootService retry 链；
  ② 给 BootService 增补 `LOCKED_BOOT_COMPLETED` 等广播，争取冷启动即恢复（该固件大概率也不发）。
