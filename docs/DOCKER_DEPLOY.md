# Docker 部署说明

## 1. 安装 Docker

服务器需要安装：

- Docker
- Docker Compose v2

验证：

```bash
docker --version
docker compose version
```

## 2. 启动系统

在项目根目录执行：

```bash
docker compose up -d --build
```

启动后访问：

```text
前端：http://服务器IP:5173
后端：http://服务器IP:8080/api/templates
```

## 3. 服务组成

| 服务 | 容器名 | 端口 | 说明 |
| --- | --- | --- | --- |
| frontend | contract-frontend | 5173 -> 80 | Vue 静态页面 + Nginx API 代理 |
| backend | contract-backend | 8080 -> 8080 | Spring Boot 后端 |
| mysql | contract-mysql | 3306 -> 3306 | MySQL 数据库 |
| redis | contract-redis | 6379 -> 6379 | Redis 缓存 |

## 4. 常用命令

查看容器：

```bash
docker compose ps
```

查看日志：

```bash
docker compose logs -f backend
docker compose logs -f frontend
```

重启：

```bash
docker compose restart
```

停止：

```bash
docker compose down
```

停止并删除数据卷：

```bash
docker compose down -v
```

## 5. 公司员工访问

开发/内网测试可以直接访问：

```text
http://服务器IP:5173
```

正式生产建议配置域名和 HTTPS：

```text
https://contract.company.com
```

Kimi Agent Tool 地址建议配置为：

```text
https://contract.company.com/api/ai/tools/call
```

如果前后端分开域名，也可以配置为：

```text
https://contract-api.company.com/api/ai/tools/call
```

## 6. 上线前必须修改

`docker-compose.yml` 里的数据库密码现在是示例值，上线前必须修改：

```yaml
MYSQL_ROOT_PASSWORD: contract_root_password
MYSQL_USER: contract_user
MYSQL_PASSWORD: contract_password
```

并同步修改 `backend` 服务里的：

```yaml
MYSQL_USER
MYSQL_PASSWORD
```

## 7. 防火墙

如果公司员工打不开，需要放开服务器端口：

- `5173`: 前端访问
- `8080`: 后端 API，生产环境可不对外开放，只通过 Nginx 代理访问

正式生产推荐只暴露 `80/443`。

