<div align="center">
<img width="1200" height="475" alt="GHBanner" src="https://ai.google.dev/static/site-assets/images/share-ais-513315318.png" />

# Zanqiwu App: PokeClaw Productivity & Automation Agent Suite
### 智能待办与设备自动化代理套件

[![Version](https://img.shields.io/badge/version-v1.22-blue.svg)](file:///d:/work/app/app/build.gradle.kts)
[![API Level](https://img.shields.io/badge/Android-API%2028%2B-green.svg)](file:///d:/work/app/app/build.gradle.kts)
[![License](https://img.shields.io/badge/License-Apache%202.0-orange.svg)](file:///d:/work/app/pokeclaw-agent/LICENSE)

---

[**English Version**](#english-documentation) | [**中文文档**](#中文说明文档)

</div>

---

# English Documentation

Welcome to **Zanqiwu App (PokeClaw)**, an advanced Android productivity and autonomous device automation ecosystem. This project splits into a high-utility Todo Application (`app`) and an Accessibility Service-based Automation Client (`pokeclaw-agent`).

## 1. Project Modules & Work Scope

The workspace is modularized into two Gradle sub-projects:

### 📱 `app` (Jetpack Compose Todo Client)
A productivity client incorporating calendar syncing, alarms, map visualizers, and state-of-the-art AI interactions.
*   **Modern Compose UI**: Designed with fluid theme styles (Cosmic, Forest, Sakura, Aurora, Sunset) and responsive transitions.
*   **Baidu Map Integration**: Pin tasks to geographic coordinates, featuring clustering, split-screen task carousels, and camera centering.
*   **Gemini AI Planner**: 
    *   *AI Task Breakdown*: Generates structured sub-tasks with categories, location coordinates, and cover illustration prompts.
    *   *Procedural Vector Art*: Generates custom coordinates (circles, rectangles, stars, triangles) drawn directly on Android canvas to save as task covers offline.
    *   *AI Songwriter*: Composes chord progressions, lyrics, and synthesizes midi-like audio sequences using `AudioSynthPlayer`.
    *   *Weekly Reports*: Summarizes finished tasks into customized professional work summaries.
*   **Productivity Tools**: Foreground service Pomodoro timers, daily rollover trackers for rollover candidates, and automatic native system calendar/alarm scheduling.

### 🤖 `pokeclaw-agent` (Autonomous Automation Agent)
A background automation library that performs low-level device actions using Android Accessibility Services.
*   **ReAct Loop Execution**: Runs multi-step execution loops on a background thread utilizing LLM tool-calling capabilities.
*   **Toolbox**: Integrates tools for UI inspection (`get_screen_info`, `find_node_info`), gestures (`tap`, `swipe`, `scroll_to_find`), communications (`send_message`, `auto_reply`), system keys, notifications, and key-value knowledge bases.
*   **Safety Guards**: Custom semantic interceptors (`DirectDeviceDataGuard`, `InAppSearchGuard`) to prevent model hallucination and premature task terminations.
*   **Stuck & Loop Detector**: 5-signal rolling window analyzer featuring 3-level escalations (HINT, STRATEGY_SWITCH, AUTO_KILL) to recover the agent from execution lockups.

---

## 2. Versioning & Subscriptions

### 🏷️ Current Release
*   **Version Name**: `1.22` (Semantic Versioning `1.22.x` format)
*   **Version Code**: `23`
*   **Min SDK**: `28` (Android 9.0 Pie)
*   **Target SDK**: `36` (Android 15+)

### 🔔 Update Subscriptions
PokeClaw features an integrated update subscription check:
*   **Automatic Checks**: The application checks for newer releases once per day in the background via the GitHub API:
    `https://api.github.com/repos/agents-io/PokeClaw/releases/latest`
*   **Manual Subscriptions**: 
    *   To subscribe to latest releases and source code updates, click the **Watch** button on our GitHub Repository page and select **Custom -> Releases**.
    *   New versions are distributed directly as signed `.apk` installers through GitHub Release assets.

---

## 3. Changelog & Recent Updates

### 🚀 v1.22 (Current Version)
*   **Fallback Model Chain**: Implemented fallback configurations in `GeminiManager` (`gemini-3.5-flash` $\rightarrow$ `gemini-3.1-flash-lite-preview` $\rightarrow$ `gemini-2.5-flash`) to ensure request availability under high concurrency.
*   **Auto-Attach Screen (Opt-3)**: The agent now automatically captures a layout hierarchy and computes screen diffs immediately after executing an action tool, saving 1 reasoning round per turn.
*   **History Compression Algorithm**: Automatically strips redundant screen hierarchy entries and compresses legacy tool responses into compact summaries, saving up to 50% on input tokens.
*   **Stuck Detector Improvements**: Integrated `StuckDetector` with HINT, STRATEGY_SWITCH, and AUTO_KILL recovery levels.

### 📅 v1.20 - v1.21
*   **Baidu Map Picker integration**: Introduced split-screen interactive picking and list-map synchronization carousel.
*   **Synthesizer Music Player**: Developed procedural melody composer integrating lyrics and chords.
*   **Pomodoro Service**: Implemented foreground status notification tracking.

---

## 4. Run Locally

### Prerequisites
*   [Android Studio](https://developer.android.com/studio) (Koala or later recommended)
*   An Android Emulator or physical device running Android 9.0+

### Steps
1.  **Clone & Open**: Open Android Studio, select **Open**, and choose this directory.
2.  **Gradle Sync**: Allow the IDE to sync and download dependencies.
3.  **API Key Configuration**:
    *   Create a file named `.env` in the root directory.
    *   Add your API keys (e.g. `GEMINI_API_KEY=your_key_here`). See `.env.example` for details.
4.  **Signing Configuration**:
    *   Before deploying a custom debug release, remove this line from the app's `build.gradle.kts`:
        `signingConfig = signingConfigs.getByName("debugConfig")`
5.  **Run**: Click **Run 'app'** (`Shift + F10`) to deploy to your emulator or physical device.

---
---

# 中文说明文档

欢迎使用 **Zanqiwu App (PokeClaw)**。本项目是一个结合了现代待办规划（Todo Client）与自主无障碍控制代理（Accessibility Automation Agent）的智能 Android 效率应用套件。

## 1. 项目模块与工作划分

整个项目通过 Gradle 分为两个核心模块：

### 📱 `app` (Jetpack Compose 待办客户端)
一个整合了日程同步、智能闹钟、物理地图标记与前沿 AI 规划的多功能待办应用。
*   **现代化 Compose UI**：内置太空蓝（Cosmic）、森林绿（Forest）、樱花粉（Sakura）、极光青（Aurora）与夕阳红（Sunset）渐变主题，支持紧凑与标准显示切换。
*   **百度地图深度集成**：支持将待办项绑定至地理坐标，提供双向联动卡片流、标记聚合以及自动聚焦。
*   **Gemini AI 助理**：
    *   *AI 智能规划*：根据用户目标自动拆解任务，输出品类、经纬度坐标及插图 Prompt。
    *   *Canvas 几何艺术画*：由 AI 计算坐标，在 Android 画布上生成各种矢量的几何艺术图（圆、方、星、三角），离线保存为任务插图。
    *   *AI 作曲音乐合成*：生成和弦、歌词，并通过 `AudioSynthPlayer` 合成类似 MIDI 的旋律进行播放。
    *   *周报生成*：自动提炼一周已完成的工作，生成专业结构化的职场汇报文案。
*   **效率工具箱**：支持番茄钟前台服务通知、昨日未完成任务每日跨天留转（Rollover）以及自动同步到系统日历与闹钟。

### 🤖 `pokeclaw-agent` (无障碍自主代理)
基于 Android Accessibility Services 开发的后台代理控制库，允许 AI 代理自主操控设备。
*   **ReAct 循环执行器**：在后台线程上开启推理-执行循环，解析 LLM 的 Tool Calling（工具调用）请求。
*   **丰富的自动化工具**：内置屏幕结构解析 (`get_screen_info`)、模拟物理点击 (`tap_node`)、滚动寻找 (`scroll_to_find`)、消息发送与自动回复、读取剪贴板、系统键模拟及本地知识库等工具。
*   **语义与安全保护 (Guards)**：通过 `DirectDeviceDataGuard` 等语义拦截器，防止大模型在缺乏环境信息时编造数据或产生“无设备访问权限”的错误拒绝，以及在编辑中途提早 Finish。
*   **卡住与死循环检测**：通过 5 种独立信号进行滑动窗口检测，并实施 3 级恢复响应（HINT 提示、STRATEGY_SWITCH 方案切换、AUTO_KILL 强杀保护）。

---

## 2. 版本与更新订阅

### 🏷️ 当前版本
*   **版本名称**: `1.22` (语义化版本 `1.22.x` 格式)
*   **版本代码**: `23`
*   **最低支持 API**: `28` (Android 9.0)
*   **目标 API**: `36` (Android 15+)

### 🔔 更新订阅与获取
PokeClaw 内部集成了自动更新监测：
*   **自动检查**：应用每天会在后台静默访问 GitHub Releases 接口：
    `https://api.github.com/repos/agents-io/PokeClaw/releases/latest`
    检测是否有更新版本。如果存在，会弹窗提示下载。
*   **手动订阅**：
    *   建议在 GitHub 项目主页点击 **Watch** 并勾选 **Custom -> Releases** 选项，以便第一时间在 GitHub 收到版本发布通知。
    *   更新包会以打包并签名的 `.apk` 安装文件形式发布在 GitHub Releases 的 Assets 资源中。

---

## 3. 历史更新日志 (Changelog)

### 🚀 v1.22 (当前版本)
*   **备用模型容灾链**：在 `GeminiManager` 中加入降级模型序列（`gemini-3.5-flash` $\rightarrow$ `gemini-3.1-flash-lite-preview` $\rightarrow$ `gemini-2.5-flash`），确保高并发下服务的可用性。
*   **自动附加屏幕状态 (Opt-3)**：在执行点击、输入、打开 App 等“动作类工具”后，代理会自动静默等待 500ms 并直接抓取最新的屏幕状态与文字变更 Diff 并返回，**每步节省 1 个推理回合**。
*   **历史 Token 压缩算法**：合并冗余的历史屏幕结构，将非最近轮次的工具返回结果压缩为精简的单行概述，**大幅节省 Prompt 资源占用（最高达 50%）**。
*   **防卡死策略升级**：优化了 StuckDetector 策略，新增 Level 1 (HINT) 到 Level 3 (AUTO_KILL) 梯级容错。

### 📅 v1.20 - v1.21
*   **百度地图标点联动**：支持双向选择与列表卡片流。
*   **AI 音乐合成播放**：支持和弦序列及多音高合成。
*   **番茄钟后台常驻**：引入前台 Foreground Service 状态栏计时。

---

## 4. 本地运行步骤

### 运行环境准备
*   安装最新版的 [Android Studio](https://developer.android.com/studio)
*   准备 Android 9.0 及以上版本的真机或模拟器

### 步骤
1.  **导入项目**：打开 Android Studio，选择 **Open** 并指向本目录。
2.  **同步构建**：等待 Gradle 完成依赖包的拉取和项目配置。
3.  **配置 API 密钥**：
    *   在项目根目录下创建一个名为 `.env` 的文件。
    *   加入您的 Gemini API 密钥：`GEMINI_API_KEY=你的API_KEY`（详见 `.env.example` 文件）。
4.  **调试证书配置**：
    *   如果打包时遇到签名冲突，可移除 `app/build.gradle.kts` 中的这一行代码：
        `signingConfig = signingConfigs.getByName("debugConfig")`
5.  **开始运行**：点击工具栏 of **Run** (`Shift + F10`) 即可将 App 部署到您的设备上。
