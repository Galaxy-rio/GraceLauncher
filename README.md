# Grace Launcher

一款使用 Kotlin 与 Jetpack Compose 编写的极简列表式 Android 启动器。当前版本先完成可运行的产品骨架：主屏幕、收藏应用、日期与日程、完整应用列表和 A–Z 快速索引。

## 已实现

- 可被 Android 设为默认“主屏幕应用”
- 读取、排序并启动设备上已安装的应用
- Niagara 风格的纵向收藏列表；右侧仅显示拥有应用的字母分组
- 常驻字母索引支持从主屏幕连续拖入应用列表、波浪反馈、反向拖动与星标返回
- 从主屏幕上滑打开应用列表；长按应用可固定到主屏幕或取消固定
- 应用列表只保留图标、名称和无底色的分组字母
- 系统壁纸透出，并叠加适合文字阅读的渐变遮罩
- 简洁的细体时钟、短日期；点击时钟打开闹钟，点击日期连接或打开日历
- 可选日历权限；主页仅显示最近一条日程的标题与相对剩余时间，无日程时隐藏
- 英文与简体中文界面

天气暂未接入，也没有声明网络权限。

## 代码结构

```text
data/
  AppRepository.kt       已安装应用枚举、图标读取与启动
  CalendarRepository.kt  Calendar Provider 日程读取
  FavoritesStore.kt      收藏应用持久化
ui/
  LauncherViewModel.kt   主界面状态与业务入口
  LauncherScreen.kt      主屏幕/应用列表路由与动效
  home/HomeScreen.kt     日期、日程和收藏列表
  drawer/AppListModel.kt  真实字母分组与列表位置映射
  drawer/AppDrawerScreen.kt  透明背景的分组列表
  components/AlphabetRail.kt  主屏幕与抽屉共用的连续触摸索引
  components/LauncherComponents.kt  图标与应用行
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

当前字母分组支持拉丁字母与去除重音符号后的首字母，中文等其他名称暂放入 `#` 分组。主屏幕无日程时不显示授权提示，首次点击日期可授予日历权限。

## 后续方向

- 收藏应用拖拽排序与布局编辑模式
- 更完整的多语言首字母分组
- 工作资料与 Private Space 支持
- 通知预览、媒体卡片与小组件
- 壁纸明暗检测、主题编辑器与图标包
- 安装/卸载广播监听和图标缓存预热

交互结构参考 [Niagara Launcher](https://niagaralauncher.app/) 与开源项目 [Victoria Launcher](https://github.com/adelmonte/victoria-launcher)。项目没有复制它们的专有素材。
