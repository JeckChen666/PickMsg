# 构建与开发

[返回文档导航](README.md) · [项目首页](../README.md)

本文面向项目维护。日常使用步骤见首页，视觉与组件约定见 [设计规范](design/DESIGN_SYSTEM.md)。

## 1. 环境准备

- JDK 17。
- Android SDK：API 35，项目 `minSdk = 26`、`targetSdk = 35`。
- 使用仓库自带的 Gradle Wrapper，不要求另装指定版本的全局 Gradle。

```bash
git clone https://github.com/JeckChen666/PickMsg.git
cd PickMsg
```

在根目录创建 `local.properties`，填写本机 SDK 路径，例如：

```properties
sdk.dir=/absolute/path/to/android-sdk
```

如果命令行找不到 Java，先将 `JAVA_HOME` 指向本机 JDK 17。`local.properties` 是本机配置，路径不要照抄其他开发机器。

## 2. 常用命令

| 目的 | 命令 | 输出 |
| --- | --- | --- |
| 调试包 | `./gradlew :app:assembleDebug` | `app/build/outputs/apk/debug/app-debug.apk` |
| 单元测试 | `./gradlew :app:testDebugUnitTest` | `app/build/reports/tests/testDebugUnitTest/` |
| 静态检查 | `./gradlew :app:lintDebug` | `app/build/reports/lint-results-debug.html` |
| 正式包 | `./gradlew :app:assembleRelease` | `app/build/outputs/apk/release/` |

签名、SDK 和版本配置由 `app/build.gradle.kts` 管理；不要在文档中维护第二套版本号。

## 3. 签名与更新安装

在仓库根目录创建 `keystore.properties`：

```properties
storeFile=keystore/pickmsg-release.jks
storePassword=你的密码
keyAlias=pickmsg
keyPassword=你的密码
```

将 keystore 放到对应位置。当前 Gradle 配置在该文件存在时使用 release 签名；不存在时，release 构建回退使用 debug 签名。keystore 目录及属性文件按现有 `.gitignore` 处理。

**更新已安装的应用必须保持签名一致。** 签名不一致通常会导致覆盖安装失败；卸载旧版后再安装才可能丢失原有本地数据，不应把卸载当作默认解决方式。

本地 JSON 备份只覆盖规则、场景、计划任务，不能作为通知历史的完整恢复手段。

## 4. 测试与验收边界

单元测试聚焦规则匹配、格式化、模板、敏感占位判定、序列化、CSV 与通知分组等核心逻辑。执行结果以本次测试报告为准，最近一次汇总见 [界面精修验收报告](design/acceptance/ACCEPTANCE.md)。

界面检查脚本位于 `scripts/ui_acceptance.py`，要求专用 `pickmsg_ui_*` AVD。运行条件、参数和数据隔离要求见验收报告；不要对日常设备清空数据来重跑脚本。

当前 CI（`.github/workflows/ci.yml`）运行单元测试、构建 Debug APK，并上传 APK 和测试结果；**没有执行 Lint 或真机验收**。因此 CI 通过不等于这些检查也通过。2026-10-29 记录的 Lint 阻断项仍需单独处理，详细列表见验收报告。

涉及真实通知、后台保活、外发、权限或恢复流程的修改，需要另外执行 [真机测试清单](real-device-testing.md)。

## 5. 技术栈与源码入口

Kotlin 2.0 · Jetpack Compose / Material 3 · Room + KSP · WorkManager · Glance · JavaMail-Android。

```text
app/src/main/java/com/pickmsg/app/
├── data/       数据实体、DAO、数据库迁移、设置、备份与 CSV
├── service/    通知采集、规则匹配、场景求值、动作、计划调度和磁贴
├── work/       周期备份任务
├── widget/     Glance 桌面小组件
└── ui/         主页面、编辑/详情页、列表分组与共用主题
    └── theme/  颜色、设计常量、基础组件和半透明材质
```

数据库迁移链为 v1 → v2 → v3；规则动作使用 JSON 列保存。修改数据结构时保留迁移路径，不通过重建数据库解决升级问题。

## 6. 实现注意事项

以下是现有实现的维护背景，不是新增功能承诺，也不代表每项都在最近一轮做过真机验证。

### 采集与平台限制

- 通知监听回调应尽快返回，内容提取、入库和规则处理走异步管道；不要在回调里加入阻塞操作。
- 内容提取先尝试展开文本、去重合并的消息行和普通文本，extras 无可用内容时再离屏解析 RemoteViews，避免每条通知都做高成本解析。
- Android 15 会对部分敏感通知脱敏。拿到占位内容时只记录、不外发，不能将占位符当成原文。
- 自身重发通知需要按包名跳过，避免再次命中规则形成循环。
- 通知去重依赖 `sbnKey + postTime`；同一通知更新后时间变化，可能形成新记录。
- 系统可能主动重绑监听服务；需要区分短暂重连与持续断开，不应假定服务永不重连。
- Wi-Fi 场景按接入类型判断，不仅依赖 `VALIDATED`，以兼容需要网页登录的网络。

### 规则与动作

- 标题/内容默认使用“且”。如果改成“或”，未设置的一侧为无条件时可能让规则匹配所有通知。
- 同一场景的条件按“且”组合；空场景不满足条件。多个绑定场景如何组合由规则的场景匹配方式决定。只统计启用的绑定场景；无绑定或启用的绑定集合为空时，当前实现直接放行场景限制。
- 格式化先于动作执行；单个动作失败不应阻断其他动作。发送类动作复用 `Retrier`，当前默认重试间隔为 5/10/15/15/15 秒，避免在页面中另写一套转发逻辑。
- 正则编译失败不要把 null 写入 `ConcurrentHashMap` 缓存；相关逻辑需要回归测试。
- 分组列表每组只生成一个唯一标题键，保留组内记录顺序，避免交错应用引起重复 LazyColumn 键。

### 数据、依赖与系统组件

- Room 失效通知主要跟踪应用自身的写入；外部直接改 SQLite 不等于应用中的 Flow 会刷新。
- 隐藏通知使用 snooze，隐藏后不再出现在活跃通知列表；恢复依赖会话内通知 key 缓存，不能承诺重启后随时恢复。
- JavaMail 两个依赖包的 META-INF 冲突由 Gradle packaging 排除项处理。
- Glance 的 `provideGlance` 是成员方法，`provideContent` 是顶层扩展；桌面小组件不直接复用 Compose 页面组件。
- 前台服务渠道的重要性影响通知操作按钮的展示；渠道重要性创建后不能简单原地修改，调整时需核对渠道 ID 与旧安装行为。
