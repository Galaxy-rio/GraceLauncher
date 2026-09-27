# Grace Launcher：F-Droid 发布步骤

采用与 SudokuYou 相同的流程：你在本机签名并发布 GitHub APK，F-Droid 从源码重建、验证可复现性后使用开发者签名。私钥、密码和 keystore 都不上传给 F-Droid。

应用源码和商店图片继续放在 GitHub；GitLab 的 `fdroid/fdroiddata` 只接收构建元数据，不需要搬迁应用仓库。

## 已准备的材料

| 材料 | 位置 |
| --- | --- |
| 英文名称、简介、介绍、更新说明 | `fastlane/metadata/android/en-US/` |
| 简体中文文案 | `fastlane/metadata/android/zh-CN/` |
| 四张英文截图 | `fastlane/metadata/android/en-US/images/phoneScreenshots/` |
| 发布记录 | `CHANGELOG.md` |
| GitLab 元数据模板 | `fdroid/com.galaxyrio.gracelauncher.yml.template` |
| 从公开 APK 生成提交文件 | `scripts/prepare-fdroid.ps1` |
| 重新生成商店截图 | `scripts/capture-store-screenshots.ps1` |
| Material Symbols 天气图标来源与 Apache-2.0 许可 | `third_party/material-symbols/NOTICE.md`、`LICENSE` |
| 内置字体来源与 OFL-1.1 许可 | `third_party/fonts/NOTICE.md`、`licenses/` |

截图顺序：`01-home.png` 主页、`02-agenda.png` 日程与天气、`03-music.png` 音乐、`04-app-list-c.png` 字母 C。全部为 1080 × 2400 的模拟器原始截图，使用实际生产界面和英文示例内容；没有后期合成界面。示例应用图标来自项目现有 Material Symbols，日程、歌曲和天气为虚构展示数据，不随发布包提供。README 直接引用这四张图片。

2026-09-27 本机验证结果：33 项单元测试、5 项字体回退测试和 1 项中文大字号日程界面测试通过，截图任务通过，`lintRelease` 为 0 错误、262 警告（其中 239 项为缺失翻译），`assembleRelease` 和 APK 对齐检查通过。天气图标的官方路径、浅色/深色界面以及发布包内的许可均已核对。四张 PNG 的完整性、尺寸和两种语言的文案长度均已核对。签名辅助脚本已通过语法检查并验证会拒绝调试签名；正式签名的完整流程和 `fdroid lint/build` 尚待实际发布与 GitLab CI 验证。

天气图标已替换为 Google 官方 Material Symbols Outlined（Apache-2.0），12 个本地矢量资源覆盖全部已知天气及晴天、多云的昼夜变化。旧天气素材已移除，来源、固定版本和许可均已记录，这一项已完成。

中日韩文字使用系统字体，内置的 6 个其他字体共 3,985,596 字节（3.80 MiB）。Release 已启用 R8 代码和资源优化，许可证文本仍保留在 APK 中；当前本机未签名包为 7,601,988 字节（7.25 MiB）。

## 提交前仍需完成

1. **你完成正式签名。** 尚未创建发布 tag，也没有本应用的正式签名 APK，因此不能填写最终提交哈希和证书指纹。不要直接照抄 SudokuYou 的指纹，除非签名 APK 实际验证得到相同值。
2. **GitLab/Linux 构建验证。** 本机的构建、测试和截图不能代替 F-Droid Linux 环境下的可复现性验证；需要查看 GitLab CI 的重建和签名比较结果。

## 1. 确定版本并提交源码

当前准备版本：

- applicationId：`com.galaxyrio.gracelauncher`
- versionName：`1.0.0`
- versionCode：`1`
- tag：`v1.0.0`
- GitHub 发行文件名：`GraceLauncher_1.0.0.apk`
- 代码许可证：按现有 README 的 GNU GPL v3.0 声明填写 `GPL-3.0-only`，不擅自改成“或更高版本”。

先检查本次改动，再提交准备材料和构建配置。商店文案和截图必须包含在发布 tag 指向的提交里，F-Droid 不会使用尚未发布的分支内容。

```powershell
git diff
git status --short
git add -A
git commit -m "Prepare Grace Launcher 1.0.0 for F-Droid"
git tag -a v1.0.0 -m "Grace Launcher 1.0.0"
git push origin master
git push origin v1.0.0
```

如工作区另有无关改动，请在 `git add -A` 前分开处理。如你实际使用其他分支，请替换 `master`。已被 F-Droid 使用的 tag 不要移动，后续改动发布新版本。

## 2. 从最终 tag 重新构建

安装 OpenJDK 21、Android SDK Platform 37（本机包名 `platforms;android-37.0`）、Build Tools 36.1.0，以及用于签名的 Build Tools 34.0.0。可在 Android Studio 的 SDK Manager 中安装。仓库使用 Gradle 9.5.0，保留了发行包 SHA-256 校验。

```powershell
# JAVA_HOME 指向你的 OpenJDK 21 安装目录。
git checkout v1.0.0
.\gradlew.bat clean :app:testDebugUnitTest :app:lintRelease :app:assembleRelease
```

得到 `app/build/outputs/apk/release/app-release-unsigned.apk`。务必使用最终 tag 重建后得到的 APK；准备期间生成的包不能证明对应最终发布提交。

构建配置已禁用 APK/AAB 依赖元数据，统一 OpenJDK 21 和 Build Tools 36.1.0，移除 Foojay 自动下载，并用 `.gitattributes` 统一文本换行。尚未补齐的社区翻译会报告 Lint 警告，运行时回退到英文；其他 Lint 错误仍会阻止检查通过。

## 3. 你在本机签名

以下操作由你执行，替换自己的 keystore 路径及 alias。密码在工具提示时输入，不写在命令、Git 或聊天里。若已经发布过 Grace Launcher 的正式 APK，继续使用同一把发布密钥。

```powershell
$releaseSdk = Join-Path $env:LOCALAPPDATA 'Android\Sdk'
New-Item -ItemType Directory -Path 'build\release' -Force | Out-Null

& "$releaseSdk\build-tools\36.1.0\zipalign.exe" -c -v 4 `
  'app\build\outputs\apk\release\app-release-unsigned.apk'

& "$releaseSdk\build-tools\34.0.0\apksigner.bat" sign `
  --ks 'D:\你的密钥目录\release.jks' `
  --ks-key-alias '你的alias' `
  --v4-signing-enabled false `
  --out 'build\release\GraceLauncher_1.0.0.apk' `
  'app\build\outputs\apk\release\app-release-unsigned.apk'

& "$releaseSdk\build-tools\34.0.0\apksigner.bat" verify --verbose --print-certs `
  'build\release\GraceLauncher_1.0.0.apk'
```

每条命令都应成功后再继续。这里只指定签名工具 34.0.0，应用的编译工具仍为 36.1.0；F-Droid 的可复现构建文档记录了部分较新签名工具与 `apksigcopier` 的兼容性问题。

输出中的 `Signer #1 certificate SHA-256 digest` 是公开的签名证书指纹，可以提交。APK 文件本身的 SHA-256 不是这个值。签名后不要再修改或重新对齐 APK。

## 4. 创建 GitHub Release

1. 打开 [GraceLauncher Releases](https://github.com/Galaxy-rio/GraceLauncher/releases)，选择 **Draft a new release**。
2. 选择已经推送的 `v1.0.0`，标题填写 `Grace Launcher 1.0.0`。
3. 填入 `CHANGELOG.md` 的首版说明。
4. 上传 `build/release/GraceLauncher_1.0.0.apk`，保持文件名完全一致，再发布 Release。
5. 确认不登录账号也能下载该 APK。模板中的下载规则为 `releases/download/v%v/GraceLauncher_%v.apk`。

如果稍后 Linux 重建不一致，应先诊断差异并重新测试。不要通过关闭签名比较或换用调试密钥来绕过检查。

## 5. 生成需要粘贴到 GitLab 的 YAML

在工作区干净、HEAD 位于 `v1.0.0` 时执行：

```powershell
.\scripts\prepare-fdroid.ps1 -SignedApk '.\build\release\GraceLauncher_1.0.0.apk'
```

脚本只读取 APK、Git tag 和 SDK 工具，不读取密钥。它会拒绝无效签名、调试签名、错误包名/版本和未提交源码，然后输出：

```text
build/fdroid/com.galaxyrio.gracelauncher.yml
```

最终文件包含 tag 指向的完整 commit SHA 和 APK 的公开证书 SHA-256。这里只验证了身份和签名，没有证明 Linux 构建已经可复现。

也可以手动复制模板，把 `@RELEASE_COMMIT@` 替换为 `git rev-parse 'v1.0.0^{commit}'` 的结果，把 `@SIGNING_CERTIFICATE_SHA256@` 替换为 `apksigner` 的证书指纹。不要把带占位符的 `.template` 文件直接提交。

## 6. 在 GitLab 提交

1. 登录 GitLab，打开 [fdroid/fdroiddata](https://gitlab.com/fdroid/fdroiddata)。如果你发布 SudokuYou 时已经 Fork 过它，直接使用原来的 Fork。
2. 先把 Fork 的默认分支与上游同步，在最新分支上创建 `add-grace-launcher`。不要把 SudokuYou 的旧修改一并带入。
3. 在网页编辑器或 Web IDE 中新建 `metadata/com.galaxyrio.gracelauncher.yml`，粘贴上一步生成的 YAML。
4. 提交到 `add-grace-launcher`，提交信息可填 `New App: Grace Launcher`。这个分支只需新增该 YAML；截图和文案已经在应用的 GitHub release tag 中。
5. 打开 Fork 的 **Build → Pipelines**（旧界面可能是 **CI/CD → Pipelines**），查看检查结果。如 Fork 没有可用 Runner 或账号要求验证，按 GitLab 的页面提示由你处理；这与应用签名密钥无关。
6. 新建 Merge Request：源项目为你的 Fork，源分支 `add-grace-launcher`；目标项目 `fdroid/fdroiddata`，目标分支 `master`。标题使用 `New App: Grace Launcher`。
7. 若 GitLab 提供应用收录模板，保留它的检查项，按实际验证结果填写。可附上 `docs/FDROID-MR.md` 的英文说明，但不要提前勾选尚未完成的授权或重建检查。
8. 关注 MR 的讨论和 Pipeline。常见问题包括新 SDK/Gradle 尚未进入服务器环境、素材许可、签名指纹错误，以及 APK 重建差异。修改同一分支即可更新 MR，不需要重复新建请求。
9. 审核合并后等待 F-Droid 的正式构建、签名验证和仓库发布。MR 合并和商店可见不是同一步；可在 [F-Droid Monitor](https://monitor.f-droid.org/) 查看构建进度，不保证固定完成时间。

## 后续版本

递增 `versionCode`，更新 `versionName`、`CHANGELOG.md` 及两个语言的 `changelogs/<versionCode>.txt`，提交后创建 `v<versionName>` tag，使用同一发布密钥上传 `GraceLauncher_<versionName>.apk`。F-Droid 的 `UpdateCheckMode` 会匹配正式版本 tag。

本次模板和 `prepare-fdroid.ps1` 针对首版 `1.0.0/1`；若首版版本号有变，需同步修改这两个文件及 changelog 文件名。截图保留在 `en-US` 下即可，无需另外制作中文图片。

重新截图可在专用英文模拟器上执行 `scripts/capture-store-screenshots.ps1`。测试会安装并可能卸载同包名的调试应用，请使用测试模拟器。截图保存于模拟器 Download 目录，再复制到 Fastlane。

## 参考

- [F-Droid 提交流程](https://f-droid.org/en/docs/Submitting_to_F-Droid_Quick_Start_Guide/)
- [文案与截图格式](https://f-droid.org/en/docs/All_About_Descriptions_Graphics_and_Screenshots/)
- [构建元数据说明](https://f-droid.org/en/docs/Build_Metadata_Reference/)
- [可复现构建与签名工具注意事项](https://f-droid.org/en/docs/Reproducible_Builds/)
- [收录与素材要求](https://f-droid.org/en/docs/Inclusion_Policy/)
