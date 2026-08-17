# 桔子扫描 (JuZiScan) 🍊

> 简洁、高效、全栈开源的智能文档扫描与 OCR 处理应用。
> 包含 **Android 移动客户端** 与 **FastAPI 开源后端**。

[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)
[![Kotlin](https://img.shields.io/badge/Kotlin-Android-blue.svg)](https://kotlinlang.org/)
[![FastAPI](https://img.shields.io/badge/FastAPI-0.100+-green.svg)](https://fastapi.tiangolo.com/)

---

## 🌟 项目亮点

- 📄 **智能文档扫描**：自动边缘检测、透视矫正与文档去阴影增强。
- 🔍 **离线/在线 OCR 文字识别**：高效提取文档文字并支持复制导出。
- 🛠️ **丰富 PDF/图像工具箱**：图片转 PDF、长图拼接、图片压缩、水印添加、二维码生成。
- 🌐 **局域网 Wi-Fi 直传**：电脑与手机无需数据线即可快速传输文档文件。
- 🔐 **全栈用户与会员系统**：支持手机号/密码登录、VIP 会员状态管理、支付宝支付对接。
- ⚡ **后端零依赖秒级启动**：后端内置 SQLite3 开箱即用，无需配置复杂的数据库服务即可本地试用。

---

## 📁 目录结构

```text
JuZiScan/
├── app/                  # Android 客户端代码 (Kotlin, Jetpack Compose, Material3)
├── backend/              # Python FastAPI 后端服务代码
│   ├── app/              # 后端核心业务逻辑与 API
│   ├── deploy/           # Nginx 生产环境配置示例
│   ├── requirements.txt  # Python 依赖清单
│   └── README.md         # 后端独立运行文档
├── build.gradle.kts      # Android 根构建脚本
├── settings.gradle.kts   # Gradle 项目设置
├── LICENSE               # MIT 开源协议
└── README.md             # 全栈项目说明文档
```

---

## 🚀 快速开始

### 1. Android 客户端 (Android Studio)

1. 使用 **Android Studio** 打开 `JuZiScan` 根目录文件夹。
2. 推荐 JDK 版本：`17` 或更高。
3. 等待 Gradle Sync 完成后，直接运行 `app` 模块至 Android 模拟器或真机即可。

### 2. FastAPI 后端 API (零配置 SQLite 启动)

进入 `backend/` 子目录：

```bash
cd backend

# 创建虚拟环境并安装依赖
python3 -m venv .venv
source .venv/bin/activate
pip install -r requirements.txt

# 复制环境变量模板
cp .env.example .env

# 启动后端服务
uvicorn app.main:app --reload --host 0.0.0.0 --port 18001
```

- **后台管理系统**：[http://127.0.0.1:18001/](http://127.0.0.1:18001/)
- **Swagger API 文档**：[http://127.0.0.1:18001/docs](http://127.0.0.1:18001/docs)

*(生产环境如需使用 PostgreSQL，仅需在 `backend/.env` 中修改 `DATABASE_URL` 即可。)*

---

## 📄 开源协议

本项目基于 [MIT License](LICENSE) 协议开源。
