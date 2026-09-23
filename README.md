# Grace Launcher

一款使用 Kotlin 与 Jetpack Compose 编写的极简列表式 Android 启动器。当前版本先完成可运行的产品骨架：主屏幕、收藏应用、日期与日程、完整应用列表和 A–Z 快速索引。

## 已实现

- 可被 Android 设为默认“主屏幕应用”
- 读取、排序并启动设备上已安装的应用
- Niagara 风格的纵向收藏列表；右侧仅显示拥有应用的字母分组
- 常驻字母索引支持从主屏幕连续拖入应用列表、波浪反馈、反向拖动与星标返回
- 仅右侧字母索引可进入抽屉；主页上下滑只滚动收藏，右下按钮打开设置
- 普通抽屉占满可用屏幕；按住字母时临时只显示该组，松手或取消触摸后恢复完整列表，并定位到该组
- 返回键或索引星标回到主页；无障碍点击字母直接定位完整列表
- 应用列表只保留图标、名称和无底色的分组字母，无标题、返回按钮和搜索栏
- 保留系统壁纸；文字自动跟随壁纸明暗提示，也可在设置中手动选择浅色/深色文字
- 大号细体时钟、短日期和实时电量；点击时钟打开闹钟
- 可选日历权限；主页仅显示最近一条日程的标题与相对剩余时间，无日程时隐藏
- 点击日期或日程打开底部日程面板：未来 14 天、日期分组、日历颜色、全天/跨日日程；支持打开日程与通过系统日历新建日程
- 应用右滑时快捷方式浮层随手指连续展开（越过系统触摸阈值后拖动 72dp 即可完全展开），反向拖回可取消；收藏/按下时预取真实 shortcut，缓存与合并查询，不显示加载条
- 应用行保留 8dp 图标内边距并使用标准 Material 圆角 ripple；长按打开底部操作面板：编辑收藏、应用信息、使用时间、分类、卸载，以及高级选项中的重命名/商店页面
- 快捷方式、设置、收藏和日程条目使用圆角裁切的点击反馈与内部留白；圆形设置 FAB 使用 Material 3 原生组件
- 操作图标使用 Google 官方 Material Symbols Outlined 矢量资源（来源和 Apache 2.0 许可证见 `third_party/material-symbols`）
- HOME 与应用列表入口分离；应用及 shortcut 接入 `LauncherApps` 和基于图标位置的公开 `ActivityOptions.makeClipRevealAnimation`
- 收藏、应用别名、分类和外观设置保存在本地；分类列表可从 Grace 设置中打开
- 支持 Android 13+ 应用提供的单色主题图标，缺少单色资源时保留原始图标
- 英文与简体中文界面

天气暂未接入，也没有声明网络权限。

## 代码结构

```text
data/
  AppRepository.kt       已安装应用枚举、图标读取与启动
  CalendarRepository.kt  Calendar Provider 日程读取
  FavoritesStore.kt      收藏应用持久化
  LauncherPreferences.kt 应用别名、分类和外观持久化
  ShortcutRepository.kt  系统快捷方式查询、预取、失效监听与启动
  AsyncQueryCache.kt     有界缓存、并发限制与查询合并
platform/
  AppLaunchTransition.kt 图标坐标转换和公开启动动画参数
ui/
  LauncherViewModel.kt   主界面状态与业务入口
  LauncherScreen.kt      主屏幕/应用列表路由与动效
  home/HomeScreen.kt     日期、日程和收藏列表
  drawer/AppListModel.kt  真实字母分组、临时筛选和全列表定位
  drawer/AppDrawerScreen.kt  透明背景的分组列表
  components/AlphabetRail.kt  主屏幕与抽屉共用的连续触摸索引
  components/LauncherComponents.kt  图标与应用行
  overlays/              快捷方式浮层、应用操作与日程底部面板
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

当前字母分组支持拉丁字母与去除重音符号后的首字母，中文等其他名称暂放入 `#` 分组。主屏幕无日程时不显示授权提示，日期面板中的“连接日历”按钮才会申请读取权限；不申请日历写入权限，新建操作交给系统日历确认。

系统 shortcut 通常要求应用成为默认启动器。未获得权限时会显示说明与设置默认桌面的入口；没有 shortcut 的应用会显示空状态，不伪造账号或快捷方式。当前只处理主用户资料。使用时间页面取决于系统是否提供对应设置 Activity；卸载始终通过系统确认，不静默卸载。

应用保留设备原有壁纸，不捆绑参考截图中的字体、图标包或品牌图形；时钟使用系统细体，主题图标只使用各应用公开的单色资源。

系统转场的能力边界：普通第三方启动器可提供公开的打开动画参数，但不能通过公开 SDK 接管 Quickstep/Recents 的交互式返回图标动画。实际打开/关闭效果仍由 Android/OEM 决定；拆分 HOME 入口避免普通应用任务分类，但无法承诺所有厂商导航手势下都不缩放。测试桌面入口应启动 `.LauncherEntryActivity` 或系统 HOME，不要用缺少 HOME 类别的显式 MainActivity Intent。

平台依据：[ActivityOptions](https://developer.android.com/reference/android/app/ActivityOptions)、[LauncherApps](https://developer.android.com/reference/android/content/pm/LauncherApps)、[AOSP Quickstep 权限和服务](https://android.googlesource.com/platform/packages/apps/Launcher3/+/master/quickstep/AndroidManifest.xml)。

## 后续方向

- 收藏应用拖拽排序与布局编辑模式
- 更完整的多语言首字母分组
- 工作资料与 Private Space 支持
- 通知预览、媒体卡片与小组件
- 更完整的主题编辑器与第三方图标包
- 安装/卸载广播监听和图标缓存预热

交互结构参考 [Niagara Launcher](https://niagaralauncher.app/) 与开源项目 [Victoria Launcher](https://github.com/adelmonte/victoria-launcher)。项目没有复制它们的专有素材。
