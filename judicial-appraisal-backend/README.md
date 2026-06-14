# 司法鉴定管理系统后端 (judicial-appraisal-backend)

这是一个基于 Spring Boot 构建的司法鉴定工作流系统独立后端。

## 环境要求
- Java 17
- Maven 3.9+
- MySQL 8+
- Redis (文件平台及后续阶段使用)
- MinIO (文件平台存储使用)

## 本地启动
1. 在 MySQL 中重建名为 `judicial_appraisal` 的数据库。
   ```sql
   DROP DATABASE IF EXISTS judicial_appraisal;
   CREATE DATABASE judicial_appraisal DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
   ```
2. 配置更新：检查并更新 `src/main/resources/application.yml` 中的环境配置信息（如数据库用户名、密码、端口等）。
3. 数据库初始化：项目只保留一个完整数据库文件 `src/main/resources/db/judicial_appraisal_full_dump.sql`，其中包含完整表结构、流程定义、案件样例、部门、岗位、角色、菜单权限和默认测试账号。导入命令示例：
   ```bash
   mysql -h localhost -P 3307 -u root -p judicial_appraisal < src/main/resources/db/judicial_appraisal_full_dump.sql
   ```
   导入后默认测试账号密码统一为 `123456`。项目不再维护拆分版迁移脚本；如需重置数据库，请重新导入该完整 SQL 文件。
4. 启动应用：

```bash
mvn spring-boot:run
```

## API 接口文档
项目启动后，可以通过以下路径访问接口文档：
- Swagger UI 交互式页面: `http://localhost:8080/swagger-ui.html`
- OpenAPI JSON 数据: `http://localhost:8080/v3/api-docs`
