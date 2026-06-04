# Kimi Contract Agent

企业合同智能生成系统，包含合同 Agent 编排、合同模板管理、Excel 批量生成、Word 文档生成、Kimi Tool 接入和 Chroma 知识库能力。

## 项目结构

- `backend`: Spring Boot + MySQL + Redis + Kimi Tool API
- `frontend`: Vue3 + Element Plus 合同工作台
- `docs`: 需求文档、部署说明、模板清单和工具说明
- `templates`: Word 模板占位目录
- `scripts`: 本地和云端辅助脚本

## 后端启动

```powershell
cd backend
mvn spring-boot:run
```

默认地址：

```text
http://localhost:8080
```

## 前端启动

```powershell
cd frontend
npm install
npm run dev
```

默认地址：

```text
http://localhost:5173
```

## 环境变量

后端支持通过环境变量配置数据库和 Kimi：

```text
MYSQL_HOST
MYSQL_PORT
MYSQL_DATABASE
MYSQL_USER
MYSQL_PASSWORD
KIMI_API_KEY
KIMI_MODEL
KIMI_TIMEOUT_MS
CHROMA_URL
```

## Docker 部署

```powershell
docker compose up -d --build
```

详细说明见：

- `docs/DOCKER_DEPLOY.md`
- `docs/MYSQL_CLOUD_SETUP.md`
- `docs/CHROMA_SETUP.md`

## 当前能力

- 合同 Agent 对话工作台
- 合同模板字段校验
- Excel 发票数据批量生成合同
- 合同编号按日期流水生成
- Word 合同文档生成
- Kimi API 自然语言字段提取
- Kimi Tool HTTP 接口
- Chroma 知识库检索
- 产品购销合同条款变体生成
