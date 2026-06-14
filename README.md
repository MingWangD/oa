# 司法鉴定管理系统 (Judicial Appraisal OA)

这是一个基于 Spring Boot 和 Vue 3 + TypeScript 的司法鉴定业务管理系统，包含完整的权限管理、动态表单/流程设计、业务流转、文档归档等核心业务功能。

## 环境要求

- **Java**: 17+
- **Node.js**: 18+
- **MySQL**: 8.0+
- **Redis**: 7.0+
- **Maven**: 3.8+

## 启动指南

### 1. 数据库配置与导入

1. 请先在 MySQL 中创建名为 `judicial_appraisal` 的数据库。
2. 配置数据库连接：检查 `judicial-appraisal-backend/src/main/resources/application.yml`，确保 `spring.datasource.username` 和 `spring.datasource.password` 与你的本地环境一致（默认端口 3307，密码 123456）。
3. **导入数据**：
   如果你在本地获取到了完整的 SQL 备份文件（如 `judicial_appraisal_full_dump.sql`），可以使用以下命令导入：
   ```bash
   mysql -h localhost -P 3307 -u root -p judicial_appraisal < judicial_appraisal_full_dump.sql
   ```
   *注意：如果未导入完整 SQL，后端启动时会自动执行 `db/` 下的迁移脚本生成基础表结构和配置种子数据。*

### 2. 后端启动 (Spring Boot)

进入后端目录，使用 Maven 编译并启动服务：

```bash
cd judicial-appraisal-backend
# 如果需要跳过测试构建
mvn clean install -DskipTests
# 启动后端服务
mvn spring-boot:run
```
后端服务默认运行在 `http://localhost:8080`。Swagger 接口文档位于 `http://localhost:8080/swagger-ui.html`。

### 3. 前端启动 (Vue 3 + Vite)

进入前端目录，安装依赖并启动开发服务器：

```bash
cd judicial-appraisal-frontend
npm install
npm run dev
```
前端服务默认运行在 `http://localhost:5173`。

## 功能测试与流程验证

后端包含了大量业务流程的运行级测试（例如独立发起、审核退回、财务报销、材料归档等 E2E 验证）。
你可以通过 Maven 运行这些测试，确保代码拉取后能够正确执行流程流转：

```bash
cd judicial-appraisal-backend
mvn test
```

## 默认账号说明

前端服务启动后，访问 `http://localhost:5173/login`。
系统中预设了多种角色账号以便进行流程流转测试（默认密码一般为 `123456`，视安全配置而定）：
- `admin` (系统管理员)
- `finance` (财务)
- `archivist` (档案管理员)
- `project_leader` (项目负责人)
- `project_assistant` (项目辅助人)
- `dept_leader` (部门负责人)
