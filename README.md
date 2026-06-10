# 完整 OA 系统重构进度

更新时间：2026-06-10

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
- 已完成前端依赖安装

#### 文档与规范

- 已整理重构阶段计划：[`docs/complete-oa-reconstruction-plan.md`](./docs/complete-oa-reconstruction-plan.md)
- 已补充中文 `AGENT.md`，记录工作习惯、优先级和约束
- 已拉取 `anthropics/skills` 到本地：`vendor/anthropic-skills`

#### 后端

- 已补充认证相关 DTO 与查询接口
- 已补充组织与管理员相关实体、DTO、Mapper
- 已新增平台目录接口，提供：
  - OA 模块树
  - 重构阶段计划
  - 司法鉴定目录
  - 司法鉴定角色清单
- 已补齐工作流候选人相关模型与 Mapper
- 已修复后端编译与测试所需的缺失能力
- 后端 `mvn test` 已通过

#### 前端

- 已补充登录、首页、个人信息、用户管理、案件详情等视图
- 已扩展路由与主框架菜单
- 已补充平台目录相关 API
- 已扩展样式，覆盖登录页、模块卡片、阶段视图、详情页等
- 前端 `npm run build` 已通过

## 进行中

- 完整 OA 公共平台能力继续补全
- 动态表单设计器
- 动态流程设计器
- 权限与数据权限体系
- 文件、归档、审计能力完善
- 司法鉴定 20 个流程、19 个表单的细化落地

## 下一阶段重点

1. 先把公共平台能力补完整
2. 再推进流程中心、知识管理、报表中心、系统管理等通用模块
3. 之后继续做司法鉴定业务的高保真流程还原
4. 最后做迁移、联调、权限校验和全量验收

## 已知验证

- `JAVA_HOME=/opt/homebrew/opt/openjdk@17 PATH="/opt/homebrew/opt/openjdk@17/bin:$PATH" mvn test`
- `npm run build`

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
