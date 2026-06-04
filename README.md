# Kimi Contract Agent

企业合同智能生成系统工程骨架，包含：

- `backend`: Spring Boot + MySQL + Redis + 合同 Tool API
- `frontend`: Vue3 + ElementPlus 合同工作台
- `docs`: 项目需求文档、合同模板清单、技术选型确认
- `templates`: Word 模板占位目录

## 快速启动

### 后端

```powershell
cd backend
mvn spring-boot:run
```

默认地址：`http://localhost:8080`

### 前端

```powershell
cd frontend
npm install
npm run dev
```

默认地址：`http://localhost:5173`

## Docker 部署

```powershell
docker compose up -d --build
```

访问：

- 前端：`http://localhost:5173`
- 后端：`http://localhost:8080/api/templates`

详细说明见：[Docker 部署说明](docs/DOCKER_DEPLOY.md)

## MVP 范围

当前骨架已包含：

- 合同模板查询
- 合同草稿创建
- 合同字段收集
- Kimi Agent Tool HTTP 接口
- 合同工作台页面
- 模板清单页面
- 项目需求文档与模板清单
