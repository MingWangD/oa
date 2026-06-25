# 表单权限与动态标红修复说明

更新时间：2026-06-25

## 修复背景

系统在“发起委托书 -> 收案员登记 -> 部门负责人审核”等节点流转时，存在两个上线前必须处理的问题：

- 部分必填字段没有显示红星，用户不知道当前节点必须填写哪些内容。
- 部分非当前节点负责的字段仍可编辑，例如收案员可能误改审批字段，或审批节点误改发起人填写的基础信息。

本次修复后，前后端统一按以下规则判断当前字段是否应该标红：

```text
显示必填红星 = required === true && readonly !== true && hidden !== true
```

只读字段和隐藏字段不会标红，也不会参与当前节点的必填拦截。

## 代码改动

### 后端运行时兜底

涉及文件：

- `judicial-appraisal-backend/src/main/java/com/example/judicialappraisal/workflow/service/FieldAccessRules.java`
- `judicial-appraisal-backend/src/main/java/com/example/judicialappraisal/workflow/service/WorkflowRuntimeService.java`

主要变化：

- 后端校验表单时会合并节点 `form_rule_json`、表单 `permission_schema_json`、字段分组和节点办理角色。
- 如果字段属于其他角色负责的分组，会在运行时自动推导为只读。
- 只读或隐藏字段不会触发当前节点必填校验。
- `INIT_FILL` 和 `CLERK_REGISTER` 默认要求并允许填写《使用手册》第三章“收到委托书”截图中带红星的字段；`expressNo`（快递单号）按截图为可填写但非必填：
  - `receivedDate`
  - `filingDate`
  - `clientName`
  - `caseNo`
  - `undertakingLegalPerson`
  - `institutionSelectionMethod`
  - `institutionSelectionTime`
  - `appraisalCategory`
  - `applicantName`
  - `respondentName`
  - `urgencyLevel`
  - `caseChannel`
  - `appraisalMatter`
- `DEPT_REVIEW` 默认要求填写 `entrustAccepted`。
- 已经在节点规则里显式配置过 `readonly` 或 `readOnly` 的字段不会被自动推导覆盖。
- 补了节点定义为空时的保护，避免校验阶段出现空指针。

### 数据库修复脚本

涉及文件：

- `scripts/fix_form_permissions.js`

脚本能力：

- 扫描已发布表单最新版本的 `permission_schema_json`。
- 扫描已发布审批流最新版本的节点配置。
- 对非当前节点角色负责的字段补充 `readonly: true`。
- 对发起和收案登记节点补充手册截图标星字段的 `readonly: false` 与 `required: true`。
- 保留人工显式配置，避免覆盖特殊流程的定制规则。
- 支持 dry-run、apply、verify 三种模式。

运行前提：

- 本机需要能执行 `mysql` 命令行客户端。
- 如果 `mysql` 不在 PATH 中，可以通过 `MYSQL_BIN` 指定路径。

运行方式：

```bash
node scripts/fix_form_permissions.js --dry-run
node scripts/fix_form_permissions.js --apply
node scripts/fix_form_permissions.js --verify
```

默认数据库连接：

```text
DB_HOST=localhost
DB_PORT=3307
DB_USER=root
DB_PASSWORD=123456
DB_NAME=judicial_appraisal
```

也可以通过环境变量覆盖：

```bash
DB_HOST=localhost DB_PORT=3307 DB_USER=root DB_PASSWORD=123456 DB_NAME=judicial_appraisal \
node scripts/fix_form_permissions.js --dry-run
```

指定 MySQL 客户端路径示例：

```bash
MYSQL_BIN=/opt/homebrew/opt/mysql@8.0/bin/mysql node scripts/fix_form_permissions.js --dry-run
```

模式说明：

- `--dry-run`：只打印将要修改的节点和字段，不写库。
- `--apply`：执行数据库更新。
- `--verify`：上线前检查；如果仍有节点需要修复，会以非 0 状态退出。

### 前端依赖清理

数据库修复脚本已经从前端目录移出，前端包不再依赖 `mysql2`。

当前原则：

- 前端项目只保留页面运行所需依赖。
- 数据库修复工具统一放在根目录 `scripts/`。
- 旧版修复脚本和临时查库脚本已删除，避免误用。

### 单测覆盖

涉及文件：

- `judicial-appraisal-backend/src/test/java/com/example/judicialappraisal/workflow/service/FieldAccessRulesTests.java`

新增和保留的覆盖点：

- 旧版节点默认规则仍兼容。
- 非当前节点角色的字段会被推导为只读。
- 只读字段不会显示为必填。
- 当前节点角色负责的字段保持可编辑。
- 人工显式配置不会被动态推导覆盖。

## 验证记录

已执行：

```bash
cd judicial-appraisal-backend
mvn test -Dtest=FieldAccessRulesTests,ReceivedEntrustBranchVerificationTest
```

结果：

```text
Tests run: 14, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

已执行：

```bash
cd judicial-appraisal-frontend
npm run build
```

结果：构建成功。

已执行：

```bash
node --check scripts/fix_form_permissions.js
```

结果：脚本语法检查通过。

## 人工验收建议

建议上线前按以下流程验收：

1. 发起人新建“收到委托书”：使用手册截图中带红星的字段显示红星且输入框标红；流水号、项目编号等非当前阶段字段不强制填写。
2. 收案员登记：案件号、收件日期、委托人、鉴定类别等登记字段可填写且应标红；部门负责人、项目负责人等审批字段只读且无红星。
3. 部门负责人审核：业务基础字段只读；是否受理等部门负责人字段可填写，并按规则标红。
4. 退回发起人：发起人可以修改业务基础字段，但不能修改部门负责人或项目负责人审批结果。

## 注意事项

- `serialNo` 当前仍只是表单字段，本次没有新增流水号自动生成逻辑。
- `projectNo` 不建议在发起和收案登记阶段强制填写，应在项目相关节点确认后再写入。
- 特殊流程如果需要打破默认规则，应优先在节点 `form_rule_json.fieldAuth` 中显式配置；后端运行时和修复脚本都会尊重显式配置。
