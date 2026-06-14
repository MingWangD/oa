# 司法鉴定管理系统后端 (judicial-appraisal-backend)

这是一个基于 Spring Boot 构建的司法鉴定工作流系统独立后端。

## 环境要求
- Java 17
- Maven 3.9+
- MySQL 8+
- Redis (文件平台及后续阶段使用)
- MinIO (文件平台存储使用)

## 本地启动
1. 在 MySQL 中创建名为 `judicial_appraisal` 的数据库。
2. 配置更新：检查并更新 `src/main/resources/application.yml` 中的环境配置信息（如数据库用户名、密码、端口等）。
3. 数据库初始化：直接启动项目时，应用会自动执行 `src/main/resources/db/` 目录下的迁移脚本来创建基础表结构和插入初始种子数据。如果需要全量恢复数据，请使用项目根目录下的全量导出 SQL 文件。
4. 启动应用：

```bash
mvn spring-boot:run
```

## API 接口文档
项目启动后，可以通过以下路径访问接口文档：
- Swagger UI 交互式页面: `http://localhost:8080/swagger-ui.html`
- OpenAPI JSON 数据: `http://localhost:8080/v3/api-docs`
