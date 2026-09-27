# Grace Launcher

一款使用 Kotlin 与 Jetpack Compose 编写的极简列表式 Android 启动器。当前版本先完成可运行的产品骨架：主屏幕、收藏应用、日期与日程、完整应用列表和 A–Z 快速索引。

## 已实现

- 可被 Android 设为默认“主屏幕应用”
- 读取、排序并启动设备上已安装的应用
- Niagara 风格的纵向收藏列表；右侧仅显示拥有应用的字母分组
- 常驻字母索引支持从主屏幕连续拖入应用列表、波浪反馈、反向拖动与星标返回
- 仅右侧字母索引可进入抽屉；主页上下滑只滚动收藏。右下按钮使用应用标志的主题色轮廓，点击搜索应用、长按进入设置
- 抽屉始终是一份完整连续列表；顶部有约三成屏高的可滚动留白，字母定位使用相同的顶部锚点
- 按住字母只临时隐藏其他组，保留所有条目的高度与同一个滚动位置；松手/取消时原位恢复，不切换筛选页面
- 靠近列表底部时由真实内容高度限制最大滚动位置，不填充大块底部空白把最后几组强行顶高
- 返回键或索引星标回到主页；无障碍点击字母直接定位完整列表
- 应用列表只保留图标、名称和无底色的分组字母，无标题、返回按钮和搜索栏
- 保留系统壁纸；文字自动跟随壁纸明暗提示，也可在设置中手动选择浅色/深色文字
- 大号细体时钟、短日期和实时电量；点击时钟打开闹钟
- 可选日历权限；主页仅显示最近一条日程的标题与相对剩余时间，无日程时隐藏
- 点击日期或日程打开底部日程面板：未来 14 天、日期分组、日历颜色、全天/跨日日程；支持打开日程与通过系统日历新建日程
- 应用右滑时快捷方式浮层随手指连续展开（越过系统触摸阈值后拖动 72dp 即可完全展开），反向拖回可取消；收藏/按下时预取真实 shortcut，缓存与合并查询，不显示加载条
- 应用行保留 8dp 图标内边距并使用标准 Material 圆角 ripple；长按打开底部操作面板：编辑收藏、应用信息、使用时间、添加到文件夹、卸载，以及高级选项中的重命名/商店页面
- 快捷方式、设置、收藏和日程条目使用对应容器形状的点击反馈与内部留白；圆形桌面按钮支持单击与长按
- 操作图标使用 Google 官方 Material Symbols Outlined 矢量资源（来源和 Apache 2.0 许可证见 `third_party/material-symbols`）
- HOME 与应用列表入口分离；应用及 shortcut 接入 `LauncherApps` 和基于图标位置的公开 `ActivityOptions.makeClipRevealAnimation`
- 全屏 Material 3 Expressive 设置：Productivity、Themes、Advanced、About；可折叠大标题、SegmentedListItem 分段圆角列表和本地页面导航
- Productivity 支持日程开关、隐藏应用、文件夹、日期电量开关和遵循系统的触觉反馈开关
- 隐藏应用同时从收藏、抽屉、搜索和文件夹内容中移除；隐藏不删除收藏关系或文件夹成员，取消隐藏即可恢复
- 文件夹可创建在收藏或应用列表底部，支持编辑名称/成员/位置与确认删除；点击或右滑展开，和 shortcut 共用连续展开面板；右侧 ◇ 可定位底部文件夹
- Themes 默认跟随系统动态颜色，也可选种子色生成完整调色板；深浅模式支持系统/浅色/深色，保留壁纸文字和单色图标选项
- About 包含实际应用版本、待维护者填写的更新日志、GPLv3 正文和真实依赖的许可证列表
- 新设置、隐藏应用和文件夹使用 Room 数据库本地持久化；原收藏、别名和壁纸外观偏好保留，升级不重置
- 支持 Android 13+ 应用提供的单色主题图标，缺少单色资源时保留原始图标
- Themes → Icon pack 可选择已安装的第三方图标包或恢复系统图标；收藏、应用列表、搜索、文件夹成员与应用详情共用同一套图标
- 英文与简体中文界面
- 全局 Josefin Sans 可变字体；中文、日文、韩文及其他已打包文字使用 Noto Sans 系列逐字形回退，字体随应用离线提供

天气、媒体播放器、小组件、时钟样式和字体选择暂为明确标注的占位项；Advanced 分类留空。没有声明网络权限。

## 代码结构

```text
data/
  AppRepository.kt       已安装应用枚举、图标读取与启动
  icons/                 图标包发现、appfilter 映射、日历图标、遮罩合成与有界缓存
  CalendarRepository.kt  Calendar Provider 日程读取
  FavoritesStore.kt      收藏应用持久化
  LauncherPreferences.kt 应用别名、分类和外观持久化
  LauncherDatabase.kt    Room 设置、隐藏应用、文件夹与成员关系
  LauncherSettingsRepository.kt  数据库可观察快照和事务写入
  ShortcutRepository.kt  系统快捷方式查询、预取、失效监听与启动
  AsyncQueryCache.kt     有界缓存、并发限制与查询合并
platform/
  AppLaunchTransition.kt 图标坐标转换和公开启动动画参数
ui/
  LauncherViewModel.kt   主界面状态与业务入口
  LauncherScreen.kt      主屏幕/应用列表路由与动效
  home/HomeScreen.kt     日期、日程和收藏列表
  drawer/AppListModel.kt  稳定的完整分组列表和字母位置索引
  drawer/AppDrawerScreen.kt  透明背景的分组列表
  components/AlphabetRail.kt  主屏幕与抽屉共用的连续触摸索引
  components/LauncherComponents.kt  图标与应用行
  overlays/              快捷方式浮层、应用操作与日程底部面板
  settings/              全屏分组设置、应用选择器、文件夹编辑器与 About
  search/                独立应用搜索界面（不进入抽屉）
  theme/                 动态色彩、字体与 Expressive 形状
```

## 构建

需要 Android SDK、JDK 17 或更新版本：

```powershell
.\gradlew.bat :app:assembleDebug
```

调试 APK 输出到 `app/build/outputs/apk/debug/app-debug.apk`。安装后可在 Android 的“默认应用 → 主屏幕应用”中选择 Grace Launcher。

验证命令（连接模拟器后可运行最后一项）：

```powershell
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
.\gradlew.bat :app:connectedDebugAndroidTest
```

当前字母分组支持拉丁字母与去除重音符号后的首字母，中文等其他名称暂放入 `#` 分组。主屏幕无日程时不显示授权提示；启用 Calendar Agenda 或在日期面板点击“连接日历”时申请读取权限，不申请日历写入权限，新建操作交给系统日历确认。

系统 shortcut 通常要求应用成为默认启动器。未获得权限时会显示说明与设置默认桌面的入口；没有 shortcut 的应用会显示空状态，不伪造账号或快捷方式。当前只处理主用户资料。使用时间页面取决于系统是否提供对应设置 Activity；卸载始终通过系统确认，不静默卸载。

应用保留设备原有壁纸，不捆绑参考截图中的图标包或品牌图形；第三方图标直接读取用户已安装的图标包资源，不执行图标包代码，也不复制到 APK 内。

## 第三方图标包

长按桌面右下按钮 → Themes → Icon pack，选择已安装的图标包即可生效，无需重启；选 System icons 恢复原图标。选择存入 Room，数据库从 v1 无损升级至 v2。

支持常见 ADW / Nova / Apex / Lawnchair 图标包协议，读取 `res/xml`、`res/raw` 或 `assets` 下的 `appfilter.xml`：

- 按启动 Activity 匹配，兼容完整/简写 ComponentInfo；包级兜底只采用明确或无歧义的映射。
- 支持 `calendar` 日期图标；跨日、时区变化和回到桌面时刷新。
- 未适配的应用使用图标包声明的 `iconback` / `iconmask` / `iconupon` / `scale`，未提供这些资源时保留应用原图标。
- 普通图标包保留自身颜色；明确提供单色层或声明 themed-icon 协议的图标包可随现有“主题图标”开关取色。
- 图标包安装、更新、卸载后刷新；已选择的包不可用时回退系统图标，保留选择以便重新安装后恢复。
- 后台解析，按包版本缓存映射并限制位图缓存大小；快速切换会取消过期加载，避免旧结果覆盖新选择。

已使用设备上安装的 Pure Icon Pack 验证真实资源、日期图标、兜底合成和设置切换。各图标包仍需由用户单独安装；目前不提供逐应用图标替换、图标包应用按钮协议或动态时钟指针。

协议参考：[Kvaesitso 图标包开发文档](https://kvaesitso.mm20.de/docs/developer-guide/integrations/icon-packs)。

## 字体

所有应用内 Compose 文字（包括时钟、索引、设置、底部面板和输入框）统一使用 [Josefin Sans](https://fonts.google.com/specimen/Josefin+Sans)，保留原有字号，并使用该字体支持的 100–700 可变字重。Android 10+ 使用公开 `Typeface.CustomFallbackBuilder` 按字形依次回退到 Noto Sans、完整 Noto Sans CJK、Noto Sans Arabic / Hebrew / Devanagari / Thai，因此中英文混排不会把整行改成另一种字体。完整 CJK 文件保留 `locl`，由文本语言选择中日韩地域字形。

字体均随 APK 打包，无网络请求；字体原始资源约 38.3 MiB，主要来自完整 CJK 字库。来源、SHA-256、OFL 授权和校验脚本见 [third_party/fonts/NOTICE.md](third_party/fonts/NOTICE.md)，APK 内亦包含授权声明。未打包的少数文字与 emoji 继续由系统字体兜底。

兼容限制：Android 9（API 28）没有公开的自定义字形回退链 API，仍使用 Josefin Sans，但缺字回退遵循设备的系统字体（AOSP 使用 Noto，OEM 可能不同）；不使用隐藏 API。系统拥有的权限弹窗、系统设置与 Toast 不受应用字体控制。

## 平台说明

系统转场的能力边界：普通第三方启动器可提供公开的打开动画参数，但不能通过公开 SDK 接管 Quickstep/Recents 的交互式返回图标动画。实际打开/关闭效果仍由 Android/OEM 决定；拆分 HOME 入口避免普通应用任务分类，但无法承诺所有厂商导航手势下都不缩放。桌面使用系统 HOME 入口；应用列表只展示 `.SettingsActivity`（Grace settings），不展示 HOME Activity。预览桌面请发送限定包名的 `MAIN` + `HOME` Intent，不要使用缺少 HOME 类别的显式 MainActivity Intent。

设置采用 Navigation Compose：层级切换沿逻辑方向移动 30 dp、总时长 300 ms，淡出 90 ms 后淡入 210 ms，与 Sudoku 的设置动效一致。子页面支持预测式返回的预览、取消和提交；从桌面进入设置时，根页面返回可预览桌面。独立设置 Activity 的根返回交给系统执行跨任务动画；Android 15+ 默认可用，Android 13/14 需开启系统的预测式返回开发者选项。桌面本身的返回不退出 HOME。

实现依据：[Android 预测式返回](https://developer.android.com/develop/ui/compose/system/predictive-back-setup)。

平台依据：[ActivityOptions](https://developer.android.com/reference/android/app/ActivityOptions)、[LauncherApps](https://developer.android.com/reference/android/content/pm/LauncherApps)、[AOSP Quickstep 权限和服务](https://android.googlesource.com/platform/packages/apps/Launcher3/+/master/quickstep/AndroidManifest.xml)。

## 后续方向

- 收藏应用拖拽排序与布局编辑模式
- 更完整的多语言首字母分组
- 工作资料与 Private Space 支持
- 通知预览、媒体卡片与小组件
- 更完整的主题编辑器与逐应用图标选择
- 更细粒度的应用更新与图标缓存预热

交互结构参考 [Niagara Launcher](https://niagaralauncher.app/) 与开源项目 [Victoria Launcher](https://github.com/adelmonte/victoria-launcher)。项目没有复制它们的专有素材。

## 许可证

Copyright (C) 2026 Grace Launcher contributors.

本项目的原创代码以 **GNU GPL 第 3 版（SPDX: GPL-3.0-only）** 发布。你可以依照该许可证使用、修改与再分发；本程序不提供任何担保，包括适销性或特定用途适用性的默示担保。完整条款见 [LICENSE](LICENSE)，应用内“About → Open-source licenses → GNU GPLv3 → Full text”也可离线阅读相同正文。

第三方库、字体及 Material Symbols 仍遵循各自的许可，项目的 GPL 声明不替换它们的许可。原始声明保留在 `third_party/` 及 APK 中；许可证页不再展示独立的字体或图标许可证链接。
