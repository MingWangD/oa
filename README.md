# 完整 OA 系统重构进度

更新时间：2026-06-11

## 项目目标

基于现有 `judicial-appraisal-backend` 和 `judicial-appraisal-frontend`，继续重构为完整 OA 系统，而不是只做司法鉴定模块。

当前重构遵循的优先级：

1. 《完整OA系统重构需求规格说明书.docx》
2. 《司法鉴定系统使用手册.docx》及细化流程图
3. 现有 OA 网站的实际功能和行为
4. 当前代码实现
5. `oa/docs` 中旧 Markdown 文档

## 当前状态

### 已完成

#### 基础环境

- 已确认并补齐本地构建环境
- 已安装 `OpenJDK 17`
- 已安装 `Maven`
- 已配置终端默认使用 Java 17
- 已安装并启动 `MySQL 8.0`、`Redis`、`MinIO`
- 已创建数据库 `judicial_appraisal` 并导入基础 schema
- 已导入 RBAC 二期迁移脚本 `migration_v2_rbac.sql`
- 已创建本地管理员账号：`admin / Admin123`
- 已创建 MinIO bucket：`judicial-appraisal`
- 已完成前端依赖安装

#### 文档与规范

- 已整理重构阶段计划：[`docs/complete-oa-reconstruction-plan.md`](./docs/complete-oa-reconstruction-plan.md)
- 已补充中文 `AGENT.md`，记录工作习惯、优先级和约束
- 已拉取 `anthropics/skills` 到本地：`vendor/anthropic-skills`

#### 后端

- 已补充认证相关 DTO 与查询接口
- 已补充组织与管理员相关实体、DTO、Mapper
- 已实现基于数据库的动态菜单与角色权限（RBAC）
- 已通过 `@PreAuthorize` 保护 API 接口
- 已将管理员识别从固定角色 ID 改为按 `role_code=ADMIN`
- 已完善动态菜单祖先目录补齐、深层菜单显示及按钮权限分离
- 已将数据权限限制为显式业务表范围，避免污染系统表查询
- 案件查询已支持本人、本部门、本部门及下级、全量数据范围
- 用户禁用或权限撤销后，旧 JWT 会立即失效
- 已新增平台目录接口，提供：
  - OA 模块树
  - 重构阶段计划
  - 司法鉴定目录
  - 司法鉴定角色清单
- 已补齐工作流候选人相关模型与 Mapper
- 已完成动态表单/动态流程版本化设计器后端：
  - 表单草稿、预览、发布、恢复、版本列表
  - 流程草稿、预览、发布、恢复、版本列表
  - 流程定义发布后不可变，运行实例绑定最新已发布版本
- 已完成第二阶段权限收尾：
  - 支持 `all`、`dept_sub`、`custom`、`dept`、`self` 五类数据范围
  - 支持角色自定义组织数据权限配置
  - 用户管理页可维护角色数据范围
- 已完成动态流程运行时收尾：
  - 办理时优先按 `wf_transition_def` 动态出边流转
  - 支持同一动作多条出边创建并行任务
  - 支持动态结束节点完成流程
- 已修复后端编译与测试所需的缺失能力
- 后端 `mvn test` 已通过
- 已新增权限、设计器、custom 数据权限和运行时动态流转测试，当前后端共 14 个测试通过

#### 前端

- 已补充登录、首页、个人信息、用户管理、案件详情等视图
- 已扩展路由与主框架菜单
- 已对接后端 `sys_menu` 实现侧边栏和权限控制的动态渲染
- 已支持 Element Plus 图标的动态按需加载
- 已补充平台目录相关 API
- 已扩展样式，覆盖登录页、模块卡片、阶段视图、详情页等
- 已补充动态表单设计页与动态流程设计页
- 前端 `npm run build` 已通过

## 进行中

- 完整 OA 公共平台能力继续补全
- 第三阶段：文件、知识与审计平台
- 文件、归档、审计能力完善
- 司法鉴定 20 个流程、19 个表单的细化落地

## 下一阶段重点

1. 先把公共平台能力补完整
2. 再推进流程中心、知识管理、报表中心、系统管理等通用模块
3. 之后继续做司法鉴定业务的高保真流程还原
4. 最后做迁移、联调、权限校验和全量验收

## 已知验证

- 后端：`mvn test`
- 前端：`npm run build`
- 后端运行：`http://localhost:8080`
- 前端运行：`http://127.0.0.1:5173`
- MinIO API：`http://localhost:9000`
- MinIO 控制台：`http://localhost:9001`

## 本地运行

当前本机环境已配置为：

- Java：`17.0.19`
- Maven：`3.9.16`
- Node：`v26.0.0`
- npm：`11.12.1`
- MySQL：`localhost:3307`
- Redis：`localhost:6379`
- MinIO：`localhost:9000`

启动后端：

```bash
cd judicial-appraisal-backend
mvn spring-boot:run
```

启动前端：

```bash
cd judicial-appraisal-frontend
npm run dev -- --host 127.0.0.1
```

依赖服务：

```bash
brew services start mysql@8.0
brew services start redis
launchctl bootstrap gui/$(id -u) ~/Library/LaunchAgents/com.oa.minio.plist
```

MinIO 的 Homebrew 版本在当前机器上存在二进制崩溃问题，因此本机使用 `~/bin/minio` 官方二进制和 `com.oa.minio` LaunchAgent 启动。

## 参考文件

- [`AGENT.md`](./AGENT.md)
- [`docs/complete-oa-reconstruction-plan.md`](./docs/complete-oa-reconstruction-plan.md)
- 《完整OA系统重构需求规格说明书.docx》
- 《司法鉴定系统使用手册.docx》
- `docs/`
- `webroot_decode/`

## 进度更新规则

本文件用于同步当前重构进度：

- 每完成一个阶段或一个关键模块，就更新一次
- 记录“已完成 / 进行中 / 待办”
- 构建或测试结果要保留
- 关键需求变更时，同步调整阶段计划
