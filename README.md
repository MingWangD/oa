# 司法鉴定管理系统 (Judicial Appraisal OA)

这是一个基于 Spring Boot 和 Vue 3 + TypeScript 的司法鉴定业务管理系统，包含完整的权限管理、动态表单/流程设计、业务流转、文档归档等核心业务功能。

## 环境要求概览

- **Java**: 17+
- **Node.js**: 18+
- **MySQL**: 8.0+
- **Redis**: 7.0+
- **Maven**: 3.8+

---

## 🛠️ 环境配置指引

### macOS 环境配置

在 macOS 上，推荐使用 Homebrew 来安装环境依赖。

1. **安装 Homebrew** (如果未安装):
   ```bash
   /bin/bash -c "$(curl -fsSL https://raw.githubusercontent.com/Homebrew/install/HEAD/install.sh)"
   ```

2. **安装 Java 17**:
   ```bash
   brew install openjdk@17
   sudo ln -sfn /opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk /Library/Java/JavaVirtualMachines/openjdk-17.jdk
   ```

3. **安装 Node.js (18+)**:
   ```bash
   brew install node@18
   # 根据终端提示，将 node@18 添加到环境变量 PATH 中
   ```

4. **安装 Maven**:
   ```bash
   brew install maven
   ```

5. **安装并启动 MySQL 8**:
   ```bash
   brew install mysql
   brew services start mysql
   # 初始安全设置（设置 root 密码等）
   mysql_secure_installation
   ```

6. **安装并启动 Redis**:
   ```bash
   brew install redis
   brew services start redis
   ```

### Windows 环境配置

在 Windows 上，推荐通过官方安装包或包管理工具（如 Scoop / winget）进行安装。

1. **安装 Java 17**:
   - 访问 [Oracle JDK 17 下载页面](https://www.oracle.com/java/technologies/javase/jdk17-archive-downloads.html) 或使用 OpenJDK。
   - 下载并运行 `.exe` 安装程序。
   - 确保在系统环境变量中配置了 `JAVA_HOME`，并将 `%JAVA_HOME%\bin` 添加到 `Path`。

2. **安装 Node.js (18+)**:
   - 访问 [Node.js 官网](https://nodejs.org/zh-cn/download/)，下载 v18.x 或更高版本的 Windows 安装程序（LTS版本）。
   - 安装时勾选“Add to PATH”。

3. **安装 Maven**:
   - 从 [Maven 官网](https://maven.apache.org/download.cgi) 下载最新版本的 ZIP 文件。
   - 解压至本地目录（例如 `C:\Program Files\Apache\maven`）。
   - 在系统环境变量中新建 `MAVEN_HOME` 指向解压目录，并将 `%MAVEN_HOME%\bin` 添加到 `Path`。

4. **安装 MySQL 8**:
   - 访问 [MySQL Installer 下载页面](https://dev.mysql.com/downloads/installer/) 下载安装包。
   - 按照向导安装 MySQL Server，设置 root 用户密码（请记住该密码，后续将在配置文件中使用）。

5. **安装 Redis**:
   - 官方 Redis 不直接支持 Windows。推荐通过 [Memurai](https://github.com/microsoft/Memurai-Developer) 或适用于 Windows 的 [Redis 移植版 (如 tporadowski/redis)](https://github.com/tporadowski/redis/releases) 安装。
   - 或者，如果你开启了 WSL2 (Windows Subsystem for Linux)，可以在 Linux 终端中运行：`sudo apt-get install redis-server` 并启动它。

---

## 🚀 启动指南

### 1. 数据库配置与导入

1. 打开命令行或 MySQL 客户端工具，登录 MySQL 并创建名为 `judicial_appraisal` 的数据库：
   ```sql
   CREATE DATABASE judicial_appraisal DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
   ```
2. **配置数据库连接**：
   打开代码文件 `judicial-appraisal-backend/src/main/resources/application.yml`。
   找到 `spring.datasource` 节点，修改为你在本地设置的用户名、密码和端口：
   ```yaml
   spring:
     datasource:
       # 默认配置为 3307 端口，如果你的本地 MySQL 是默认 3306 端口，请修改此处
       url: jdbc:mysql://localhost:3307/judicial_appraisal?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai
       username: root
       password: 123456 # 替换为你的 MySQL 密码
   ```
3. **导入数据**：
   如果你获取到了完整的 SQL 备份文件（如 `judicial_appraisal_full_dump.sql`），请在命令行中导入：
   ```bash
   # 请根据实际端口和密码情况调整命令
   mysql -h localhost -P 3307 -u root -p judicial_appraisal < judicial_appraisal_full_dump.sql
   ```
   *注意：如果未导入完整 SQL 备份文件，后端启动时会自动执行 `db/` 目录下的 Flyway 迁移脚本，生成基础表结构并插入初始配置与角色种子数据。*

### 2. 后端启动 (Spring Boot)

打开你的终端（Windows 下使用 CMD/PowerShell，macOS 下使用 Terminal）：

```bash
cd judicial-appraisal-backend
# 编译项目（首次启动或修改依赖后）
mvn clean install -DskipTests
# 启动后端服务
mvn spring-boot:run
```
后端服务默认运行在 `http://localhost:8080`。
你可以访问 `http://localhost:8080/swagger-ui.html` 查看接口文档。

### 3. 前端启动 (Vue 3 + Vite)

新开一个终端窗口：

```bash
cd judicial-appraisal-frontend
# 安装依赖 (推荐使用 npm，如果你熟悉也可以使用 pnpm 或 yarn)
npm install
# 启动本地开发服务器
npm run dev
```
前端服务默认运行在 `http://localhost:5173`。

---

## 🧪 功能测试与流程验证

后端包含了大量业务流程的运行级回归测试（例如：收到委托书流转、财务报销、材料归档等自动化 E2E 验证）。
你可以通过 Maven 运行这些测试，确保你的环境正确并且代码能够顺利执行核心流程逻辑：

```bash
cd judicial-appraisal-backend
mvn test
```

## 👥 默认账号说明

前端服务启动后，在浏览器访问 `http://localhost:5173/login`。
为了方便进行多角色业务流程的协同流转测试，系统中预设了以下角色账号（默认密码一般为 `123456`）：

- `admin` (系统管理员 - 拥有全量权限)
- `finance` (财务 - 处理退费、报销等)
- `archivist` (档案管理员 - 处理用章、发函、归档等)
- `project_leader` (项目负责人 - 案件流程的主理人，负责核心节点审批)
- `project_assistant` (项目辅助人 - 协助编制初稿、文书及现场记录)
- `dept_leader` (部门负责人 - F类项目及重大流转的审核节点)
- `tech_leader` (技术负责人 - 处理复杂技术把关审核)