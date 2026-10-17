# 工具入口与闪光灯 v1.0 修复验证

## 行为

- 从“全部工具”进入的扫描识别、图片处理和效率工具，结束或关闭后返回“全部工具”；首页入口仍返回首页。
- 四个图片处理入口从“全部工具”点击后直接打开系统相册。首次取消返回工具列表；编辑时取消重选保留原图与编辑内容。首页入口保留原有选图方式。
- 拼接只选择一张时提示后重新选图。多选图片有读取失败时明确提示，不生成缺图的拼图或 PDF。
- 闪光灯“开启”控制取景补光，“关闭”关闭补光，“自动”沿用拍照自动闪光。退出或进入后台关灯，返回前台及切换拍摄模式后恢复所选模式。
- 闪光灯异步请求随模式和相机生命周期失效，防止旧请求取消时误关当前灯光。

## 验证方式与工件

设备：OnePlus PKR110，Android 16。使用真实 Activity、系统相册、CameraX 绑定相机；不清空应用数据。

```powershell
./gradlew.bat :app:assembleDebug :app:assembleDebugAndroidTest --no-daemon --max-workers=2 --console=plain
./tools/run-firefly-icons-e2e.ps1 -Adb <adb.exe绝对路径> -Serial <设备序列号> -ArtifactDirectory output/tool-navigation-e2e
```

Debug 构建通过，见 [构建日志](images/tool-navigation-v1/build-debug.txt)。真机自动化 7 项全部通过（37.531 秒），见 [设备日志](images/tool-navigation-v1/instrumentation.txt)。覆盖首页与全部工具返回、首次相册取消、文档预览/排序/编辑/导出和图标渲染。闪光灯使用 CameraX `torchState` 核对开启、关闭、自动、连续循环、后台关闭、前台恢复、自动拍模式及退出关闭，见 [状态记录](images/tool-navigation-v1/camera-flash-state.txt)。代码复核未发现 P1/P2 问题。

图片选择与重选取消部分由用户确认已自行测试，不计入自动化通过项。系统图库对新建测试相册的定位不稳定，相应自动化原型已撤除；自动化保留相册直达、首次取消、入口返回和闪光灯回归。产品页截图取自真实 Compose 窗口，系统相册截图取自设备屏幕；本地工件位于 `output/tool-navigation-e2e/`，固定名称复跑覆盖。

![从工具返回后的全部工具页面](images/tool-navigation-v1/tools-returned.png)

## 边界

此次为单台真机自动化检查与截图复核，并记录用户确认的手工验证。未重新进行 OCR/证件/表格识别质量、真实分享接收、实体打印及所有机型的完整验收。

补光实现核对了 [CameraControl 官方说明](https://developer.android.com/reference/androidx/camera/core/CameraControl#enableTorch(boolean))，使用现有 CameraX 1.4.2 API。
