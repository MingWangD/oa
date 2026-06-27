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

7. **安装并启动 MinIO**:
   ```bash
   brew install minio/stable/minio
   # 启动 MinIO 服务，指定数据存储目录（例如 ~/minio-data）
   minio server ~/minio-data --console-address ":9001"
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

6. **安装并启动 MinIO**:
   - 访问 [MinIO 下载页面](https://min.io/download#/windows) 下载 `minio.exe`。
   - 在本地新建一个文件夹作为数据存储目录（例如 `C:\minio-data`）。
   - 打开命令行（CMD/PowerShell），切换到 `minio.exe` 所在目录并运行：
     ```cmd
     minio.exe server C:\minio-data --console-address ":9001"
     ```

---

## 🚀 启动指南

### 1. 数据库配置与导入

1. 打开命令行或 MySQL 客户端工具，登录 MySQL 并创建名为 `judicial_appraisal` 的数据库：
   ```sql
   DROP DATABASE IF EXISTS judicial_appraisal;
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
3. **导入完整数据库文件**：
   项目只保留一个完整 SQL 文件，路径为：
   ```text
   judicial-appraisal-backend/src/main/resources/db/judicial_appraisal_full_dump.sql
   ```
   请在命令行中导入：
   ```bash
   # 请根据实际端口和密码情况调整命令
   mysql -h localhost -P 3307 -u root -p judicial_appraisal < judicial-appraisal-backend/src/main/resources/db/judicial_appraisal_full_dump.sql
   ```
    该文件包含完整表结构、流程定义、部门、岗位、角色、菜单权限和默认测试账号（已排除历史和样例案件数据，保持纯净初始化）。导入后默认账号密码统一为 `123456`。

   项目不再维护拆分版迁移脚本；如需重置数据库，请重新执行本节的 `DROP DATABASE`、`CREATE DATABASE` 和导入命令。

### 2. MinIO 对象存储配置 (必需)

系统在业务流转中依赖 MinIO 存储附件材料与生成的 PDF 文书，必须启动并完成以下配置：

1. **访问后台**：在浏览器中打开 `http://localhost:9001` (上方命令启动时指定的 console 端口)。
2. **登录账号**：默认 Username 和 Password 均为 `minioadmin`（若为较新版本的 MinIO，控制台启动时会输出生成的临时账号密码 `RootUser` / `RootPass`，请注意查阅终端日志）。
3. **创建 Bucket**：登录后，在界面中选择 **Buckets**，点击 **Create Bucket**。名称必须填写为 **`judicial-appraisal`**。
4. **验证代码配置**：打开 `judicial-appraisal-backend/src/main/resources/application.yml`，核对 `app.minio` 下的账号密码。如果你本地 MinIO 使用的不是 `minioadmin`，请修改它：
   ```yaml
   app:
     minio:
       endpoint: http://localhost:9000
       access-key: 你的MinIO账号
       secret-key: 你的MinIO密码
       bucket: judicial-appraisal
   ```

### 3. 后端启动 (Spring Boot)

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

### 4. 前端启动 (Vue 3 + Vite)

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

## 👥 默认账号说明

前端服务启动后，在浏览器访问 `http://localhost:5173/login`。
为了方便进行多角色业务流程的协同流转测试，系统中预设了以下角色账号，默认密码均为 `123456`：

- `admin` (系统管理员 - 拥有全量权限)
- `case_acceptor1` (收案员1 - 处理委托接收、收件等，另外还有 case_acceptor2, case_acceptor3)
- `project_leader1` (项目负责人1 - 案件流程的主理人，负责核心节点审批，另外还有 project_leader2, project_leader3)
- `project_assistant1` (项目辅助人1 - 协助编制初稿、文书及现场记录，另外还有 project_assistant2, project_assistant3)
- `dept_leader1` (部门负责人1 - F类项目及重大流转的审核节点，另外还有 dept_leader2, dept_leader3)
- `tech_leader` (技术负责人 - 处理复杂技术把关审核)
- `director_review` (审阅所长/授权审批人 - 处理授权审批类节点)
- `archivist` (档案管理员 - 处理用章、发函、归档、邮寄等)
- `center_archivist` (中心档案管理员 - 处理中心归档审核)
- `business_staff` (综合业务部 - 处理综合业务类事项)
- `finance` (财务 - 处理退费、报销等)

---

## 🔒 动态表单权限与必填配置

本项目实现了严格的节点级表单字段级权限隔离。不同审批节点（如“收案员登记”、“项目负责人审核”）的“必填星号”和“字段只读”状态，完全由后端动态解析下发：

1. **核心规则**：对于任意一个审批节点，如果某个字段不在其显式规定的“必填(required)”或“可选(optional)”名单中，该字段将**强制变为只读 (`readonly: true`)**。这确保了如“后续节点绝对无法篡改发起人填写的表单”的业务隔离要求。
2. **表单模板与配置项修改**：
   - 系统所有的表单模板、流程节点挂载及权限规则均在后端 `JudicialConfigImportService.java` 中集中声明和维护。
   - 若需调整表单字段（如增加字段、修改只读权限、调整所属分组），请直接修改 `JudicialConfigImportService.java` 对应的配置代码。
   - 修改完成后，重启后端项目，并调用配置导入接口使新模板生效（并同步导出最新的初始化 SQL 文件）：
     ```bash
     # 1. 通过管理员权限调用接口，将最新的代码配置刷入数据库
     TOKEN=$(curl -s -X POST http://localhost:8080/api/auth/login -H "Content-Type: application/json" -d '{"username":"admin","password":"123456"}' | grep -o '"token":"[^"]*' | cut -d'"' -f4)
     curl -s -X POST "http://localhost:8080/api/platform/judicial-catalog/import?forceNewVersion=true" -H "Authorization: Bearer $TOKEN"
     
     # 2. 导出最新数据库配置给其他协同开发者 (请根据实际端口和密码修改)
     mysqldump -h localhost -P 3307 -u root -p123456 judicial_appraisal > ./judicial-appraisal-backend/src/main/resources/db/judicial_appraisal_full_dump.sql
     ```
