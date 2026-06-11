# OA 重构项目说明

更新时间：2026-06-11

## 这是什么项目

这是一个**完整 OA 系统重构项目**，不是单独的司法鉴定小系统。

当前仓库以现有 `oa` 项目为基础，目标是使用：

- 后端：`Spring Boot 3`、`Java 17`、`Spring Security`、`MyBatis-Plus`
- 前端：`Vue 3`、`TypeScript`、`Vite`、`Pinia`、`Element Plus`
- 基础设施：`MySQL`、`Redis`、`MinIO`

逐步重构出一个可替代旧 OA 网站的现代化系统。

业务范围包含：

- 完整 OA 公共平台能力
- 管理员账号可见的全部 OA 模块
- 司法鉴定 20 个流程、19 个表单及其细化业务规则

这不是“从零新建”的仓库，而是**保留并扩展现有代码**的持续重构工程。

## 需求优先级

出现冲突时，按下面顺序执行：

1. 《完整OA系统重构需求规格说明书.docx》
2. 《司法鉴定系统使用手册.docx》及细化流程图
3. 现有 OA 网站实际功能和行为
4. 当前仓库中的现有代码
5. `docs/` 中旧 Markdown 文档

注意：

- `docs/` 中有一部分是“司法鉴定一期简化方案”的旧文档。
- 这些旧文档可以参考结构和历史实现，但**不能缩小当前项目范围**。

## 仓库结构

```text
oa/
├── judicial-appraisal-backend/   Spring Boot 后端
├── judicial-appraisal-frontend/  Vue 3 前端
├── docs/                         分阶段计划、核对矩阵、历史设计说明
├── webroot_decode/               旧系统参考内容
├── AGENT.md                      协作约定、优先级、工作习惯
├── GEMINI.md                     Gemini 协作记录与上下文对齐
└── README.md                     当前项目总览
```

后端主要模块：

- `auth`：登录、JWT、当前用户
- `organization`：用户、角色、部门、岗位、RBAC、数据权限
- `platform`：模块目录、阶段计划、司法鉴定目录、配置导入
- `workflow`：流程定义、流程运行时、任务、子流程
- `caseinfo`：案件草稿、案件详情、案件提交流程
- `task`：任务查询
- `workbench`：我的工作、工作台统计
- `file`：文件上传、下载、预览、版本
- `knowledge`：知识库、目录、文档、自动归档
- `audit`：审计事件

前端目前主要包含：

- 登录与基础布局
- 动态菜单与权限路由
- 用户管理与角色数据权限管理
- 案件列表、案件详情、工作台
- 动态表单设计页、动态流程设计页
- 知识库页面

## 当前到底做到了什么

可以把当前进度理解成四层。

### 1. 已稳定可运行的公共底座

- 登录、JWT、Spring Security 已可用
- RBAC 菜单权限、接口权限、数据权限已落地
- 支持 `self`、`dept`、`dept_sub`、`custom`、`all` 五类数据范围
- 管理员识别按 `role_code=ADMIN`，不再依赖固定角色 ID
- 动态菜单由后端 `sys_menu` 驱动，前端可递归渲染
- 用户禁用或权限撤销后，旧 JWT 会立即失效

### 2. 动态流程平台已经成型

- 动态表单支持：草稿、预览、发布、恢复、版本列表
- 动态流程支持：草稿、预览、发布、恢复、版本列表
- 流程定义发布后不可变
- 新实例可绑定已发布流程版本
- 流程运行时支持：
  - 按 `wf_transition_def` 动态流转
  - 多出边并行任务
  - 条件表达式
  - 动态结束节点
  - 候选任务、认领、转办、撤回基础能力

### 3. 文件、知识库、审计、归档闭环已经有底座

- MinIO 文件上传、下载、预览可用
- 通用文件版本表已落地
- 知识目录、知识文档、知识文档版本已落地
- 节点完成后可自动归档到知识库
- 上传、下载、预览、权限变更、归档都会写审计事件

### 4. 司法鉴定已经进入“高保真”阶段

- 已梳理出 20 个流程、19 个表单元数据
- 已支持一键导入动态设计器
- 已建立核对矩阵：`docs/judicial-appraisal-flow-verification-matrix.md`
- 已完成 `received-entrust / 收到委托书` 首轮高保真校准：
  - 真实表单字段组
  - 真实节点
  - 条件分支
  - 子流程触发
  - 子流程结束回写
- 已完成 `preliminary-survey / 初步勘验` 首轮高保真校准：
  - 现场工作方案
  - 设备出入库记录 / 设备使用记录
  - 是否具备鉴定条件分支
  - 缴费通知 / 终止鉴定子流程触发
- 已完成 `payment-notice / 发交费通知书及相关函件` 首轮高保真校准：
  - 缴费函件草稿
  - 审核与用章
  - 盖章件回传
  - 已缴费 / 未缴费分支
  - 内部质量控制 / 终止鉴定子流程触发
- 已完成 `reject-acceptance / 不予受理` 首轮高保真校准：
  - 不予受理通知书草稿
  - 项目负责人审核
  - 用章流程触发
  - 盖章通知书回传
  - 送达确认与归档子流程触发
- 已完成 `quality-control / 编制内部质量控制文件` 首轮高保真校准：
  - 内部质量控制文件草稿
  - 中心格式 / 非中心格式与合同金额字段
  - F 类项目判断
  - 项目负责人 / 部门负责人审核分支
  - 用章与盖章件回传
  - 后续六类子流程触发
- 已完成 `field-survey / 现场勘验` 首轮高保真校准：
  - 现场工作方案
  - 勘验记录
  - 设备使用与归还记录
  - 15 万金额阈值
  - 项目负责人 / 技术负责人 / 部门负责人逐级审核
  - 后续五类子流程触发
- 已完成 `material-receive-return / 材料接收与返还` 首轮高保真校准：
  - 案件号、负责人及档案管理员等基础信息
  - 材料来源、接收登记与保管信息
  - 是否补充材料与返还材料处理
  - 后续五类子流程触发（含归档）
- 已完成 `draft-opinion-review / 鉴定意见书征求意见稿送审稿编制` 首轮高保真校准：
  - 初稿编制及附件版本控制
  - 项目负责人、技术负责人、部门负责人三级串行审核
  - 三级审核不可合并控制
  - 定稿上传及流向配置
- 已完成 `final-opinion-review / 鉴定意见书送审稿编制` 首轮高保真校准：
  - 初稿编制、A版、A-B版、A-B-C版及最终送审稿版本流转
  - 项目负责人、技术负责人、部门负责人三级串行审核
  - 三级审核不可合并控制
  - 定稿上传及出具意见书流向配置
- 已完成 `issue-opinion / 出具鉴定意见书` 首轮高保真校准：
  - 承诺书与复核意见补充
  - 用章、开票、送达与归档的真实顺序流转
  - 盖章件回传、送达及归档串联
- 已完成 `issue-draft-opinion / 出具征求意见稿` 首轮高保真校准：
  - 说明函编制与盖章
  - 盖章件回传、送达
  - 反馈接收与异议流转（通过显式路由字段分支到法院函件或送审稿编制）
- 已完成 `court-letter / 收到法院其他函件（含异议函）` 首轮高保真校准：
  - 关联原流程与函件登记
  - 异议判断、回复函编制、项目负责人/部门负责人审核
  - 用章、寄送，以及流向送审稿编制/出具意见书/归档
- 已完成 `court-appearance / 收到出庭通知` 首轮高保真校准：
  - 关联原流程与出庭通知登记
  - 出庭费通知、调档、出庭准备、出庭登记
  - 出庭后材料整理，以及流向送审稿编制/出具意见书/归档
- 已完成 `withdraw-case-letter / 收到撤案函` 首轮高保真校准：
  - 撤案函登记
  - 是否退费判断
  - 流向退费或终止鉴定
- 已完成 `refund / 退费` 首轮高保真校准：
  - 合同变更、收入确认、退费申请
  - 财务打款与打款结果回传
  - 打款完成后流向终止鉴定
- 已完成 `terminate-appraisal / 终止鉴定` 首轮高保真校准：
  - 终止函/终止确认函编制
  - 项目负责人审核、用章、盖章件回传
  - 归档子流程触发
- 已完成 `archive / 归档` 首轮高保真校准：
  - 项目档案、纸质扫描件、电子归档地址整理
  - 邮寄入库 / 直接中心审核两条路径
  - 中心档案管理员审核并入库
- 已完成 `seal-application / 用章流程` 首轮高保真校准：
  - 申请人提交用章申请
  - 档案管理员审核
  - 盖章经办人处理与扫描件回传
- 已完成 `expense-reimbursement / 财务报销` 首轮高保真校准：
  - 报销事项、金额、发票汇总登记
  - 财务处理结果与实际支付时间登记
- 已完成 `case-suspension / 案件暂停` 首轮高保真校准：
  - 暂停申请与授权审批
  - 恢复办理 / 进入终止鉴定两条路径
- 已从“只记录子流程关系”升级为“按子流程定义真正起首个任务并继续流转”
- 已完成第三阶段增强项当前计划范围：
  - 文件查重命中识别
  - 基础病毒扫描拦截
  - 文本预览水印
  - 知识库全文检索（标题、归档快照、归档结果、文本内容）

## 当前哪些是真的，哪些还没做完

这是最重要的一段。

### 已经是真的

- 管理员可以登录系统
- 动态菜单、权限、用户管理、角色数据权限配置可用
- 动态表单/动态流程设计器后端和页面骨架可用
- 知识库真实接口可用
- 司法鉴定目录、流程/表单导入能力可用
- `received-entrust` 已不再是纯占位流程
- 子流程实例、子流程任务、子流程结束回写已具备真实运行语义

### 还没有完全完成

- 20 个司法鉴定流程已全部完成首轮高保真配置
- 19 个表单已全部完成当前计划范围内的高保真细化
- 复杂条件规则已覆盖当前运行时支持范围，后续如需更复杂逻辑需扩展条件表达式引擎
- 完整 OA 其它业务域还没有开始大规模落地
- 可视化拖拽式表单设计器还没做成成熟产品态
- 更强的专业级病毒引擎、二进制文档水印和更重型搜索索引仍可继续增强

换句话说：

- **平台底座已经有了。**
- **司法鉴定第一条主链已经开始高保真。**
- **完整 OA 还远没有交付完成。**

## 当前重点正在做什么

当前重构重点已经从“搭平台”进入“跑真实流程”。

现在正在推进的主线是：

1. 把 `received-entrust` 做成完整模板
2. 把它分出的关键真实路径逐条拉到高保真：
   - `preliminary-survey` 已完成首轮校准
   - `payment-notice` 已完成首轮校准
   - `reject-acceptance` 已完成首轮校准
   - `quality-control` 已完成首轮校准
   - `field-survey` 已完成首轮校准
   - `material-receive-return` 已完成首轮校准
   - `draft-opinion-review` 已完成首轮校准
   - `final-opinion-review` 已完成首轮校准
   - `issue-opinion` 已完成首轮校准
   - `issue-draft-opinion` 已完成首轮校准
   - `court-letter` 已完成首轮校准
   - `court-appearance` 已完成首轮校准
3. 用真实案件联调整条司法鉴定主链和父流程回写
4. 再逐步扩展到完整 OA 其它业务域

## 给项目组的当前交接

当前请按“第三阶段已完成、第四阶段已完成首轮高保真配置并进入真实联调准备”理解项目状态。

短期建议从“继续补流程”切换到“真实案件联调与父流程回写细化”，下一条优先做：

   - 真实案件联调：`received-entrust -> preliminary-survey / payment-notice / reject-acceptance`
   - 父流程回写细化：`refund / terminate-appraisal / archive / seal-application`

原因：

- 现在高保真配置已经齐了，下一步价值最高的是验证真实流转和父子流程回写是否符合旧 OA 行为。
- 这也是第三、第四阶段从“代码完成”走向“业务可验收”的关键一步。

继续开发时不要把目标缩小成司法鉴定小系统。当前项目目标仍然是重构旧 OA 网站，司法鉴定流程只是当前最优先落地和验证的平台样板。

## 当前进度判断

按项目分阶段计划，当前大致处于：

- 第一阶段：已完成
- 第二阶段：已完成
- 第三阶段：已完成
- 第四阶段：已完成首轮高保真配置，进入真实流程联调准备
- 第五、第六阶段：尚未开始

更详细的阶段计划见：

- [`docs/complete-oa-reconstruction-plan.md`](./docs/complete-oa-reconstruction-plan.md)

更详细的司法鉴定验收基线见：

- [`docs/judicial-appraisal-flow-verification-matrix.md`](./docs/judicial-appraisal-flow-verification-matrix.md)

## 如何在本机运行

### 环境

当前本机已验证环境：

- `Java 17.0.19`
- `Maven 3.9.16`
- `Node v26.0.0`
- `npm 11.12.1`
- `MySQL localhost:3307`
- `Redis localhost:6379`
- `MinIO localhost:9000`

### 启动依赖

```bash
brew services start mysql@8.0
brew services start redis
launchctl bootstrap gui/$(id -u) ~/Library/LaunchAgents/com.oa.minio.plist
```

说明：

- MinIO 当前使用 `~/bin/minio` 官方二进制 + LaunchAgent 启动。
- 本机 Homebrew 版本的 MinIO 之前出现过二进制崩溃，不作为默认方案。

### 启动后端

```bash
cd judicial-appraisal-backend
mvn spring-boot:run
```

后端默认地址：

- `http://localhost:8080`

### 启动前端

```bash
cd judicial-appraisal-frontend
npm run dev -- --host 127.0.0.1
```

前端默认地址：

- `http://127.0.0.1:5173`

### 常用验证

后端测试：

```bash
cd judicial-appraisal-backend
mvn test
```

前端构建：

```bash
cd judicial-appraisal-frontend
npm run build
```

当前最新验证结果：

- 后端 `mvn test` 通过，20 个测试全绿
- 前端 `npm run build` 通过

## 数据库迁移说明

当前已经使用或新增过的关键脚本：

- `migration_v2_rbac.sql`
- `migration_v3_dynamic_platform.sql`
- `migration_v4_file_knowledge_audit.sql`
- `migration_v5_subflow_relation.sql`

如果新环境从零起，需要确保这些结构变化已进入数据库。

## 接手这个项目时，建议先看什么

建议阅读顺序：

1. `README.md`
2. `AGENT.md`
3. `GEMINI.md`
4. `docs/complete-oa-reconstruction-plan.md`
5. `docs/judicial-appraisal-flow-verification-matrix.md`

如果你是开发者，接下来优先看这些代码：

- 后端流程运行时：
  - `judicial-appraisal-backend/.../workflow/service/WorkflowRuntimeService.java`
- 司法鉴定配置导入：
  - `judicial-appraisal-backend/.../platform/service/JudicialConfigImportService.java`
- 平台目录与司法鉴定目录：
  - `judicial-appraisal-backend/.../platform/service/PlatformCatalogService.java`
- 前端 API：
  - `judicial-appraisal-frontend/src/api/judicial.ts`

## 当前已知风险

- 目前最复杂的部分不是权限，而是司法鉴定高保真流程细化
- 已导入的 20 个流程/19 个表单，不等于已经全部达到手册验收级别
- 当前运行时已经具备主流程/子流程基础，但仍需继续补更细的父流程回写和复杂分支
- 完整 OA 模块范围很大，司法鉴定之外的模块还没有进入实做阶段

## 当前下一步

最直接的下一步是：

- 用真实案件跑通 `received-entrust`
- 验证三条路径：
  - `preliminary-survey`
  - `payment-notice`
  - `reject-acceptance`
- 把它们的节点、产物、归档、状态回写都串成闭环

这一步一旦立住，后面复制到剩余司法鉴定流程会快很多。
