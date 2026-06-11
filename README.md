# MyTV

一个面向 Android TV 的央视直播播放器。

这版工程只保留内置央视官方直播频道，默认走原生播放链路，不再提供自定义视频源、远程配置页和通用 IPTV 兼容能力。

## 当前定位

- 只播放内置 CCTV 频道
- 使用 Media3 ExoPlayer 播放 HLS 直播流
- 遥控器操作优先，兼容触屏手势
- 支持频道切换、画质切换、收藏和基础显示设置
- 最低支持 Android 7.0

## 内置频道

当前内置以下频道：

- CCTV-1 综合
- CCTV-2 财经
- CCTV-3 综艺
- CCTV-4 中文国际
- CCTV-5 体育
- CCTV-6 电影
- CCTV-7 国防军事
- CCTV-8 电视剧
- CCTV-9 纪录
- CCTV-10 科教
- CCTV-11 戏曲
- CCTV-12 社会与法
- CCTV-13 新闻
- CCTV-14 少儿
- CCTV-15 音乐
- CCTV-16 奥林匹克
- CCTV-17 农业农村

## 操作方式

### 遥控器

- `OK / 中键`：打开频道菜单
- `上 / 下`：切换频道
- `左 / 右`：切换画质
- `数字键`：直接输入频道号
- `菜单 / 设置 / 帮助`：打开设置面板
- `返回`：关闭菜单或设置；连续按两次退出应用

### 触屏

- `单击`：打开频道菜单
- `双击`：打开设置面板
- `中间区域上下滑动`：切换频道
- `左侧上下滑动`：调节亮度
- `右侧上下滑动`：调节音量

## 可用设置

当前版本保留的设置项：

- 换台反转
- 换台时显示频道号
- 显示时间
- 时间是否显示秒
- 开机自启
- 启动后默认进入我的收藏
- 显示全部频道
- 紧凑菜单
- 恢复默认配置

## 技术栈

- Kotlin
- Android SDK 35
- minSdk 24
- AndroidX Media3 ExoPlayer
- OkHttp
- Gson
- Coroutines

## 构建

要求：

- JDK 17
- Android SDK 35

构建调试包：

```bash
./gradlew assembleDebug
```

Windows:

```powershell
.\gradlew.bat assembleDebug
```

安装调试包：

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## 项目说明

这个仓库基于上游工程思路收敛而来，但目标已经改成单一场景：

- 不再维护自定义源导入
- 不再维护远程网页配置
- 不再兼容通用直播源格式
- 只围绕央视直播和电视端播放体验继续迭代

## 更新记录

[HISTORY.md](./HISTORY.md)
