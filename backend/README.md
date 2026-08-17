# JuZiScan Backend API

基于 FastAPI 的 JuZiScan 后端 API 服务，包含：

- 普通用户用户名/密码登录、已绑定手机号短信登录、JWT 鉴权
- 当前用户信息、会员状态、订单与支付宝支付对接
- 管理员登录与数据统计接口
- 内置开箱即用的后台管理与协议页面
- **数据库双模式**：本地零配置支持 SQLite3，生产环境支持 PostgreSQL

## 接口规划

- App 与后台管理 API 统一走：`https://api.example.com/api/v1`
- 后台管理页面直接访问：`https://api.example.com/`
- 支付宝异步通知地址：`https://api.example.com/api/v1/payments/alipay/notify`

说明：

- Android App 的 `JUZISCAN_API_BASE_URL` 在本地模拟器可配置为 `http://10.0.2.2:18001/api/v1`
- FastAPI 默认监听内网端口，如 `127.0.0.1:18001`
- 对外可通过 Nginx 反向代理开放 `80/443`

## 本地快速运行 (SQLite3 零配置)

1. **创建虚拟环境并安装依赖**

```bash
python3 -m venv .venv
source .venv/bin/activate
pip install -r requirements.txt
```

2. **准备环境变量**

```bash
cp .env.example .env
```

默认配置下 `DATABASE_URL=sqlite:///./juziscan.db`，直接使用本地 SQLite3 数据库，无需安装任何数据库软件！

3. **启动服务**

```bash
uvicorn app.main:app --reload --host 0.0.0.0 --port 18001
```

4. **本地访问地址**

- 后台管理首页: [http://127.0.0.1:18001/](http://127.0.0.1:18001/)
- Swagger API 文档: [http://127.0.0.1:18001/docs](http://127.0.0.1:18001/docs)
- OpenAPI JSON: [http://127.0.0.1:18001/openapi.json](http://127.0.0.1:18001/openapi.json)

## 生产环境 (PostgreSQL)

若需切换至 PostgreSQL，只需在 `.env` 中配置：

```env
DATABASE_URL=postgresql+psycopg://postgres:postgres@127.0.0.1:5432/juziscan
```

## 管理员初始化

后台管理员与 App 用户为独立身份验证：

在 `.env` 中配置：

```env
BOOTSTRAP_ADMIN_EMAIL=admin@example.com
BOOTSTRAP_ADMIN_PASSWORD=replace-with-a-strong-password
BOOTSTRAP_ADMIN_NICKNAME=JuZiScan Admin
```

后台登录接口：
- `POST /api/v1/admin/auth/login`
- `GET /api/v1/admin/auth/me`

## 腾讯云短信配置

在 `.env` 中按需填入：

```env
TENCENT_SMS_APP_ID=你的短信 SDKAppID
TENCENT_SMS_SECRET_ID=你的 SecretId
TENCENT_SMS_SECRET_KEY=你的 SecretKey
TENCENT_SMS_SIGN_NAME=你的短信签名内容
TENCENT_SMS_TEMPLATE_ID=你的短信模板 ID
TENCENT_SMS_REGION=ap-guangzhou
TENCENT_SMS_TEMPLATE_PARAMS={code}
TENCENT_SMS_DEBUG_MODE=False
SMS_CODE_EXPIRE_MINUTES=5
SMS_CODE_RESEND_INTERVAL_SECONDS=60
SMS_CODE_MAX_ATTEMPTS=5
```

## 支付宝支付配置

在 `.env` 中按需填入：

```env
ALIPAY_APP_ID=你的应用AppID
ALIPAY_GATEWAY=https://openapi.alipay.com/gateway.do
ALIPAY_NOTIFY_URL=https://api.example.com/api/v1/payments/alipay/notify
ALIPAY_APP_PRIVATE_KEY=-----BEGIN PRIVATE KEY-----...
ALIPAY_ALIPAY_PUBLIC_KEY=-----BEGIN PUBLIC KEY-----...
ALIPAY_SELLER_ID=你的支付宝商户UID
LOG_LEVEL=INFO
```

仓库已提供 Nginx 部署示例文件：`deploy/nginx.conf.example`。
