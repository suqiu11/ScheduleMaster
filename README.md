# ScheduleMaster

轻量级 Android 原生工具：在设定时间通过系统 Intent 唤起指定 App。  
**不模拟点击、不修改 GPS、不使用无障碍自动操作。**

目标设备：小米红米 Note 11 Pro（MIUI / HyperOS，Android 12+）  
支持 Android 8（API 26）～ 14。

当前版本：**v1.2.1**

---

## 架构说明

```
┌─────────────────────────────────────────────────────────┐
│                        UI 层                             │
│  MainActivity ── 任务列表 / 开关 / 删除                   │
│  EditTaskActivity ── 添加/编辑 / 随机延时 / 立即测试      │
│  AppPickerActivity ── 已安装可启动 App 列表               │
└───────────────────────┬─────────────────────────────────┘
                        │
┌───────────────────────▼─────────────────────────────────┐
│                   TaskRepository                         │
│              Room (ScheduleTask + TaskDao)               │
└───────────────────────┬─────────────────────────────────┘
                        │
        ┌───────────────┼───────────────┐
        ▼               ▼               ▼
 AlarmScheduler   RandomDelayCalculator  AppLauncher
 (setAlarmClock)  (种子随机延时)         (Intent 启动)
        │
        ▼
 AlarmReceiver ──► LaunchDispatchService ──► 启动 App
 BootReceiver  ──► 开机恢复全部闹钟
 ScheduleForegroundService ──► 低优先级常驻通知保活
```

### 模块职责

| 模块 | 职责 |
|------|------|
| `ScheduleTask` | Room 实体，持久化任务配置 |
| `AlarmScheduler` | `setAlarmClock` 设置精确闹钟 |
| `RandomDelayCalculator` | 基准时间 + [min,max] 均匀随机，种子=任务ID+日期 |
| `AlarmReceiver` | 到点触发前台派发服务 |
| `LaunchDispatchService` | 全屏 Intent + 前台服务，绕过后台启动限制 |
| `LaunchTrampolineActivity` | 亮屏中转页，再启动目标 App |
| `AppLauncher` | 支持指定 LAUNCHER Activity 组件启动 |
| `ScheduleForegroundService` | 前台服务降低 MIUI 杀后台概率 |

---

## 数据模型

```kotlin
ScheduleTask(
    id: Long,
    name: String,
    packageName: String,
    activityClassName: String,  // 指定 LAUNCHER Activity，空则用默认入口
    appLabel: String,
    hour: Int,
    minute: Int,
    daysOfWeek: Int,            // bit0=周一 … bit6=周日，默认 63=周一~周六
    enabled: Boolean,
    minDelayMinutes: Int,
    maxDelayMinutes: Int
)
```

---

## 权限说明

| 权限 | 用途 |
|------|------|
| `SCHEDULE_EXACT_ALARM` | Android 12+ 精确到点闹钟 |
| `USE_FULL_SCREEN_INTENT` | 息屏/锁屏时全屏提醒并拉起 App |
| `RECEIVE_BOOT_COMPLETED` | 重启后恢复闹钟 |
| `POST_NOTIFICATIONS` | 前台服务通知（Android 13+） |
| `FOREGROUND_SERVICE` / `SPECIAL_USE` | 前台保活与派发服务 |

---

## 红米 / MIUI 必做设置

> 不完成以下设置，定时任务可能被系统延迟或无法触发。

### 1. 精确闹钟权限（Android 12+）
**设置 → 应用设置 → ScheduleMaster → 其他权限 → 闹钟与提醒 → 允许**

### 2. 自启动
**设置 → 应用设置 → 应用管理 → ScheduleMaster → 自启动 → 开启**

### 3. 省电策略
**设置 → 电池 → ScheduleMaster → 无限制**

### 4. 后台弹出界面
**设置 → 应用设置 → ScheduleMaster → 其他权限 → 后台弹出界面 → 允许**

### 5. 全屏通知（Android 14+）
**设置 → 应用设置 → ScheduleMaster → 全屏通知 → 允许**

### 6. 锁定最近任务
多任务界面下拉本 App 卡片 → 点击「锁定」

---

## 编译 APK

### 环境要求
- Android Studio Hedgehog (2023.1.1) 或更新
- JDK 17
- Android SDK 34

### 命令行

```bash
cd ScheduleMaster   # 或 TimedAppLauncher 目录
gradlew.bat assembleDebug   # Windows
./gradlew assembleDebug     # macOS / Linux
```

输出路径：`app/build/outputs/apk/debug/app-debug.apk`

---

## 使用说明

1. 安装 APK，完成 MIUI 设置（见上文）
2. 点击 **+** 添加任务：选择 App、基准时间、星期、随机延时
3. 点击 **立即测试运行** 确认能正常打开目标 App
4. 保存后任务出现在首页，到点自动唤起
5. **覆盖更新**会保留任务；**卸载重装**会清空数据

---

## 版本历史

| 版本 | 说明 |
|------|------|
| v1.2.1 | 修复后台定时无法打开 App；改用 setAlarmClock + 前台派发服务 |
| v1.2.0 | 支持同一包名多个 LAUNCHER 入口（如斩星魔剑-自动定位） |
| v1.0.0 | 初始版本 |

---

## 免责声明

本工具仅通过系统标准 Intent 在指定时间打开用户选择的 App，不提供任何界面自动化、虚拟定位或 Root 能力。  
请遵守目标 App 的服务条款及当地法律法规，合理使用。
