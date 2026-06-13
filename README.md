# 司法鉴定管理系统 (使用手册范围实现)

更新时间：2026-06-12

## 项目定位

本项目当前唯一必要目标是：**严格按照《司法鉴定系统使用手册.docx》完美实现司法鉴定管理系统全部功能。**

不再以“完整 OA 六阶段重构”为当前必要目标。合同管理 (Contract)、客户管理 (CRM)、项目管理 (Project)、人事 (HR)、行政 (Admin) 等通用 OA 模块不属于当前验收的必要范围。

## 需求依据

在实现过程中，以下文件为最高优先级依据：

1. **`docs/司法鉴定系统使用手册.docx`**：定义了 18 个流程章节（3.1 至 3.18）。
2. **`docs/judicial-appraisal-flowchart-baseline.md`**：登记主流程图与细化流程图，作为后续逐流程跑通、分支覆盖和退回路径验证的主要操作基准。
3. **`docs/judicial-appraisal-manual-based-requirements-v2.md`** (或 `exports/司法鉴定系统使用手册.txt`)：基于手册细化的需求。
4. **当前仓库中的现有代码**。
5. `docs/` 目录中的旧 Markdown 文档：**仅作历史参考，与手册冲突时以手册为准。**

注意：
- 手册正文列明 18 个流程章节，其中 3.15 提到“财务报销”为独立流程，故合计 **19 个验收流程**。
- 后续运行级验收以 `docs/flowcharts/` 中纳入的主流程图和细化流程图为主要跑通路径；流程图中可能存在如“案件暂停”等手册未列明流程，应先记录差异，不直接扩展当前必要验收范围。

## 仓库结构

```text
oa/
├── judicial-appraisal-backend/   Spring Boot 后端
├── judicial-appraisal-frontend/  Vue 3 前端
├── docs/                         使用手册、流程图基准、验收矩阵、执行计划
│   └── flowcharts/               主流程图与细化流程图
├── AGENT.md                      协作约定、优先级、工作习惯 (已对齐手册范围)
├── GEMINI.md                     Gemini 协作记录与上下文对齐 (已对齐手册范围)
└── README.md                     项目总览 (当前文件)
```

## 当前进度判断 (基于使用手册)

- **第一阶段：公共平台与可运行骨架** - **基本完成**
- **第二阶段：权限、动态表单、动态流程核心能力** - **基本完成**
- **第三阶段：文件、知识库、自动归档、审计基础闭环** - **基本完成**
- **第四阶段：司法鉴定使用手册级运行验收** - **当前主线，尚未最终完成**
- **第五阶段：通用业务模块 (合同/CRM/项目等)** - **当前非必要范围 / 仅作历史规划**
- **第六阶段：上线治理与集成** - **当前非必要范围 / 仅作历史规划**

## 如何在本机运行

### 环境要求

- Java 17, Maven 3.9+
- Node.js 18+ (建议 v20+), npm 10+
- MySQL 8.0 (默认端口 3307)
- Redis 6+ (默认端口 6379)
- MinIO (默认端口 9000)

默认后端配置见 `judicial-appraisal-backend/src/main/resources/application.yml`：

- 后端服务：`http://localhost:8080`
- 前端开发服务：`http://localhost:5173`
- MySQL：`localhost:3307/judicial_appraisal`，用户名 `root`，密码 `123456`
- Redis：`localhost:6379`，数据库 `0`
- MinIO：`http://localhost:9000`，Access Key `minioadmin`，Secret Key `minioadmin`，Bucket `judicial-appraisal`

### macOS 环境配置

推荐使用 Homebrew 安装基础依赖：

```bash
brew install openjdk@17 maven node@20 mysql@8.0 redis minio/stable/minio
```

如果 `java` 或 `node` 命令没有自动指向上述版本，可按 Homebrew 输出提示设置 `PATH`。常见设置如下：

```bash
export PATH="/opt/homebrew/opt/openjdk@17/bin:$PATH"
export PATH="/opt/homebrew/opt/node@20/bin:$PATH"
```

启动 MySQL、Redis：

```bash
brew services start mysql@8.0
brew services start redis
```

本项目默认连接 MySQL `3307` 端口。如果本机 MySQL 默认是 `3306`，可以二选一：

1. 修改 `judicial-appraisal-backend/src/main/resources/application.yml` 中的 JDBC 端口为 `3306`。
2. 将 MySQL 配置为监听 `3307`。

创建数据库和初始化表结构：

```bash
mysql -uroot -p123456 -P3307 < judicial-appraisal-backend/src/main/resources/db/schema.sql
mysql -uroot -p123456 -P3307 judicial_appraisal < judicial-appraisal-backend/src/main/resources/db/migration_v2_rbac.sql
mysql -uroot -p123456 -P3307 judicial_appraisal < judicial-appraisal-backend/src/main/resources/db/migration_v3_dynamic_platform.sql
mysql -uroot -p123456 -P3307 judicial_appraisal < judicial-appraisal-backend/src/main/resources/db/migration_v4_file_knowledge_audit.sql
mysql -uroot -p123456 -P3307 judicial_appraisal < judicial-appraisal-backend/src/main/resources/db/migration_v5_subflow_relation.sql
mysql -uroot -p123456 -P3307 judicial_appraisal < judicial-appraisal-backend/src/main/resources/db/migration_v6_form_data.sql
mysql -uroot -p123456 -P3307 judicial_appraisal < judicial-appraisal-backend/src/main/resources/db/migration_v7_contract_mvp.sql
mysql -uroot -p123456 -P3307 judicial_appraisal < judicial-appraisal-backend/src/main/resources/db/migration_v8_manual_acceptance_seed.sql
```

启动 MinIO：

```bash
mkdir -p ~/minio-data/judicial-appraisal
MINIO_ROOT_USER=minioadmin MINIO_ROOT_PASSWORD=minioadmin minio server ~/minio-data --console-address ":9001"
```

MinIO 控制台地址为 `http://localhost:9001`。首次运行后确认存在 `judicial-appraisal` bucket；如果没有，可以在控制台手动创建。

### Windows 环境配置

推荐使用 Windows 11 + PowerShell + Docker Desktop。Java、Maven、Node.js 可使用 winget 安装：

```powershell
winget install EclipseAdoptium.Temurin.17.JDK
winget install Apache.Maven
winget install OpenJS.NodeJS.LTS
```

打开新的 PowerShell，确认版本：

```powershell
java -version
mvn -version
node -v
npm -v
```

使用 Docker 启动 MySQL、Redis、MinIO：

```powershell
docker run --name judicial-mysql `
  -e MYSQL_ROOT_PASSWORD=123456 `
  -e MYSQL_DATABASE=judicial_appraisal `
  -p 3307:3306 `
  -d mysql:8.0 `
  --character-set-server=utf8mb4 `
  --collation-server=utf8mb4_general_ci

docker run --name judicial-redis `
  -p 6379:6379 `
  -d redis:7

docker run --name judicial-minio `
  -p 9000:9000 `
  -p 9001:9001 `
  -e MINIO_ROOT_USER=minioadmin `
  -e MINIO_ROOT_PASSWORD=minioadmin `
  -v "$env:USERPROFILE\minio-data:/data" `
  -d quay.io/minio/minio server /data --console-address ":9001"
```

初始化数据库：

```powershell
Get-Content judicial-appraisal-backend\src\main\resources\db\schema.sql | docker exec -i judicial-mysql mysql -uroot -p123456
Get-Content judicial-appraisal-backend\src\main\resources\db\migration_v2_rbac.sql | docker exec -i judicial-mysql mysql -uroot -p123456 judicial_appraisal
Get-Content judicial-appraisal-backend\src\main\resources\db\migration_v3_dynamic_platform.sql | docker exec -i judicial-mysql mysql -uroot -p123456 judicial_appraisal
Get-Content judicial-appraisal-backend\src\main\resources\db\migration_v4_file_knowledge_audit.sql | docker exec -i judicial-mysql mysql -uroot -p123456 judicial_appraisal
Get-Content judicial-appraisal-backend\src\main\resources\db\migration_v5_subflow_relation.sql | docker exec -i judicial-mysql mysql -uroot -p123456 judicial_appraisal
Get-Content judicial-appraisal-backend\src\main\resources\db\migration_v6_form_data.sql | docker exec -i judicial-mysql mysql -uroot -p123456 judicial_appraisal
Get-Content judicial-appraisal-backend\src\main\resources\db\migration_v7_contract_mvp.sql | docker exec -i judicial-mysql mysql -uroot -p123456 judicial_appraisal
Get-Content judicial-appraisal-backend\src\main\resources\db\migration_v8_manual_acceptance_seed.sql | docker exec -i judicial-mysql mysql -uroot -p123456 judicial_appraisal
```

MinIO 控制台地址为 `http://localhost:9001`。首次运行后确认存在 `judicial-appraisal` bucket；如果没有，可以在控制台手动创建。

### 手册验收测试账号

执行 `migration_v8_manual_acceptance_seed.sql` 后，会初始化使用手册范围内的业务角色账号。默认密码统一为 `123456`。

| 用户名 | 角色 | 主要用途 |
|---|---|---|
| `case_acceptor` | 收案员、收件人、申请人 | 发起“收到委托书”，登记法院函件、出庭通知、撤案函等收件类工作 |
| `project_leader` | 项目负责人、申请人 | 接收项目负责人节点，审核、判断分支、发起用章相关申请 |
| `project_assistant` | 项目辅助人 | 办理辅助编制、材料上传、意见稿和文书准备节点 |
| `dept_leader` | 部门负责人 | 办理部门审核、F 类项目审核等节点 |
| `tech_leader` | 技术负责人 | 办理技术审核节点 |
| `director_review` | 审阅所长 | 办理所长审阅相关节点 |
| `archivist` | 档案管理员、盖章经办人、邮寄人员 | 办理档案、用章、寄送、归档节点 |
| `center_archivist` | 中心档案管理员 | 办理中心档案审核、入库节点 |
| `business_staff` | 综合业务部、申请人 | 办理综合业务部相关工作和普通申请类工作 |
| `finance` | 财务、申请人 | 办理退费、财务报销、打款确认节点 |
| `admin` | 系统管理员 | 仅用于系统配置、用户和流程设计管理，不作为业务流程验收主账号 |

验收主流程时不要使用 `admin` 作为业务经办人。建议从 `case_acceptor` 登录发起“收到委托书”，再切换 `dept_leader`、`project_leader`、`project_assistant`、`archivist`、`finance` 等账号验证待办流转。

### 后端启动

```bash
cd judicial-appraisal-backend
mvn spring-boot:run
```

### 前端启动

```bash
cd judicial-appraisal-frontend
npm install
npm run dev
```

前端开发服务器会监听 `5173`，并将 `/api` 请求代理到后端。默认后端地址由 `judicial-appraisal-frontend/vite.config.ts` 中的代理配置决定。

### 常用验证命令

后端全量测试：

```bash
cd judicial-appraisal-backend
mvn test
```

前端构建：

```bash
cd judicial-appraisal-frontend
npm run build
```

## 核心验收项

1. **主界面与门户**：符合手册描述的入口与布局。
2. **19 个业务流程**：从委托到归档、财务、异议、出庭等全链路闭环。
3. **通用办理能力**：会签、主办/经办权限、转交、退回、流程图展示、附件去重、必填校验。
4. **知识库与归档**：流程节点产物自动按案件号归档，支持版本化与审计。
5. **查询与报表**：符合手册要求的案件查询与数据汇总展示。
