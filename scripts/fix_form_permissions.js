#!/usr/bin/env node

const { spawnSync } = require("node:child_process");

const mode = process.argv.find((arg) => ["--dry-run", "--apply", "--verify"].includes(arg));

if (!mode) {
  console.error("用法: node scripts/fix_form_permissions.js --dry-run|--apply|--verify");
  process.exit(1);
}

const ENTRUST_REGISTRATION_REQUIRED_FIELDS = new Set([
  "receivedDate",
  "filingDate",
  "clientName",
  "caseNo",
  "undertakingLegalPerson",
  "institutionSelectionMethod",
  "institutionSelectionTime",
  "appraisalCategory",
  "applicantName",
  "respondentName",
  "urgencyLevel",
  "caseChannel",
  "appraisalMatter",
]);

const ENTRUST_REGISTRATION_OPTIONAL_WRITABLE_FIELDS = new Set([
  "expressNo",
  "projectAmount",
]);
const ENTRUST_REGISTRATION_BASE_FIELDS = new Set([
  "expressNo",
  "projectAmount",
  ...ENTRUST_REGISTRATION_REQUIRED_FIELDS,
]);

const dbConfig = {
  host: process.env.DB_HOST || "localhost",
  port: process.env.DB_PORT || "3307",
  user: process.env.DB_USER || "root",
  password: process.env.DB_PASSWORD || "123456",
  database: process.env.DB_NAME || "judicial_appraisal",
};

const mysqlBin = process.env.MYSQL_BIN || "mysql";

function mysqlArgs() {
  const args = [
    "--batch",
    "--raw",
    "--skip-column-names",
    "--host",
    dbConfig.host,
    "--port",
    dbConfig.port,
    "--user",
    dbConfig.user,
    dbConfig.database,
  ];
  if (dbConfig.password) {
    args.splice(args.length - 1, 0, `--password=${dbConfig.password}`);
  }
  return args;
}

function runSql(sql) {
  const result = spawnSync(mysqlBin, mysqlArgs(), {
    input: sql,
    encoding: "utf8",
    maxBuffer: 1024 * 1024 * 64,
  });
  if (result.error) {
    throw result.error;
  }
  if (result.status !== 0) {
    throw new Error(result.stderr || result.stdout || `mysql exited with ${result.status}`);
  }
  return result.stdout.trimEnd();
}

function sqlString(value) {
  return `'${String(value)
    .replace(/\\/g, "\\\\")
    .replace(/\0/g, "\\0")
    .replace(/\n/g, "\\n")
    .replace(/\r/g, "\\r")
    .replace(/\x1a/g, "\\Z")
    .replace(/'/g, "\\'")}'`;
}

function decodeBase64(value) {
  if (!value || value === "NULL") {
    return "";
  }
  return Buffer.from(value, "base64").toString("utf8");
}

function parseRows(output, columns) {
  if (!output) {
    return [];
  }
  return output.split("\n").map((line) => {
    const values = line.split("\t");
    return Object.fromEntries(columns.map((column, index) => [column, values[index] ?? ""]));
  });
}

function parseJson(text, fallback) {
  if (!text || typeof text !== "string") {
    return fallback;
  }
  try {
    return JSON.parse(text);
  } catch {
    return fallback;
  }
}

function fieldKey(field) {
  return field.field || field.code || field.key || "";
}

function roleValues(role) {
  if (!role) {
    return [];
  }
  if (typeof role === "string") {
    return [role];
  }
  if (typeof role === "object") {
    return [role.roleName, role.roleCode, role.role].filter(Boolean);
  }
  return [String(role)];
}

function canRoleEdit(assigneeValues, allowedValues, roleNameToCode, roleCodeToName) {
  return assigneeValues.some((assignee) =>
    allowedValues.some((allowed) =>
      allowed === assignee ||
      roleNameToCode.get(allowed) === assignee ||
      roleCodeToName.get(allowed) === assignee
    )
  );
}

function buildFormConfig(row) {
  const permissionSchema = parseJson(decodeBase64(row.permission_schema_json), null);
  const fields = parseJson(decodeBase64(row.field_schema_json), null);
  if (!permissionSchema?.groups || !Array.isArray(fields)) {
    return null;
  }

  const restrictedGroups = new Map();
  Object.entries(permissionSchema.groups).forEach(([groupName, config]) => {
    const roles = Array.isArray(config?.roles) ? config.roles.flatMap(roleValues) : [];
    if (roles.length > 0) {
      restrictedGroups.set(groupName, roles);
    }
  });

  const fieldsByGroup = new Map();
  fields.forEach((field) => {
    const key = fieldKey(field);
    if (!key) {
      return;
    }
    const group = field.group || "默认组";
    const groupFields = fieldsByGroup.get(group) || [];
    groupFields.push(key);
    fieldsByGroup.set(group, groupFields);
  });

  return { restrictedGroups, fieldsByGroup };
}

function setReadonlyIfImplicit(rule, originalFieldAuth, field, matrixLog) {
  rule.fieldAuth[field] ||= {};
  const original = originalFieldAuth[field] || {};
  if (Object.prototype.hasOwnProperty.call(original, "readonly") ||
      Object.prototype.hasOwnProperty.call(original, "readOnly")) {
    matrixLog.push(`    ${field}: SKIP 显式配置 readonly=${original.readonly ?? original.readOnly}`);
    return false;
  }
  if (rule.fieldAuth[field].readonly === true) {
    matrixLog.push(`    ${field}: OK 已只读`);
    return false;
  }
  rule.fieldAuth[field].readonly = true;
  matrixLog.push(`    ${field}: READONLY 非当前节点角色字段`);
  return true;
}

function setRegistrationWritableRequired(rule, originalFieldAuth, field, matrixLog) {
  rule.fieldAuth[field] ||= {};
  const original = originalFieldAuth[field] || {};
  if (Object.prototype.hasOwnProperty.call(original, "readonly") ||
      Object.prototype.hasOwnProperty.call(original, "readOnly")) {
    matrixLog.push(`    ${field}: SKIP 保留显式配置`);
    return false;
  }

  let changed = false;
  if (rule.fieldAuth[field].readonly !== false) {
    rule.fieldAuth[field].readonly = false;
    changed = true;
  }
  if (rule.fieldAuth[field].required !== true) {
    rule.fieldAuth[field].required = true;
    changed = true;
  }
  matrixLog.push(`    ${field}: ${changed ? "WRITE+REQUIRED" : "OK"} 登记核心字段`);
  return changed;
}

function setRegistrationWritableOptional(rule, field, matrixLog) {
  rule.fieldAuth[field] ||= {};
  let changed = false;
  if (rule.fieldAuth[field].readonly !== false) {
    rule.fieldAuth[field].readonly = false;
    changed = true;
  }
  if (rule.fieldAuth[field].required !== false) {
    rule.fieldAuth[field].required = false;
    changed = true;
  }
  matrixLog.push(`    ${field}: ${changed ? "WRITE+OPTIONAL" : "OK"} 登记可填非必填字段`);
  return changed;
}

function setReceivedEntrustBaseReadonlyOptional(rule, field, matrixLog) {
  rule.fieldAuth[field] ||= {};
  let changed = false;
  if (rule.fieldAuth[field].readonly !== true) {
    rule.fieldAuth[field].readonly = true;
    changed = true;
  }
  if (rule.fieldAuth[field].required !== false) {
    rule.fieldAuth[field].required = false;
    changed = true;
  }
  matrixLog.push(`    ${field}: ${changed ? "READONLY+OPTIONAL" : "OK"} 后续节点基础字段`);
  return changed;
}

function loadRoles() {
  const output = runSql("SELECT role_name, role_code FROM sys_role WHERE deleted = 0;");
  const rows = parseRows(output, ["role_name", "role_code"]);
  const roleNameToCode = new Map();
  const roleCodeToName = new Map();
  rows.forEach((role) => {
    if (role.role_name) roleNameToCode.set(role.role_name, role.role_code);
    if (role.role_code) roleCodeToName.set(role.role_code, role.role_name);
  });
  return { roleNameToCode, roleCodeToName };
}

function loadFormConfigs() {
  const output = runSql(`
    SELECT fv.form_code,
           REPLACE(TO_BASE64(fv.field_schema_json), '\\n', ''),
           REPLACE(TO_BASE64(fv.permission_schema_json), '\\n', '')
    FROM form_version fv
    JOIN (
      SELECT form_code, MAX(id) AS id
      FROM form_version
      WHERE status = 'published' AND deleted = 0
      GROUP BY form_code
    ) latest ON latest.id = fv.id;
  `);
  const rows = parseRows(output, ["form_code", "field_schema_json", "permission_schema_json"]);
  const formConfigs = new Map();
  rows.forEach((row) => {
    const config = buildFormConfig(row);
    if (config) {
      formConfigs.set(row.form_code, config);
    }
  });
  return formConfigs;
}

function loadWorkflows() {
  const output = runSql(`
    SELECT wd.id, wd.wf_code, wd.form_code
    FROM wf_definition wd
    JOIN (
      SELECT wf_code, MAX(id) AS id
      FROM wf_definition
      WHERE deleted = 0 AND publish_status = 'published'
      GROUP BY wf_code
    ) latest ON latest.id = wd.id;
  `);
  return parseRows(output, ["id", "wf_code", "form_code"]);
}

function loadNodes(workflowId) {
  const output = runSql(`
    SELECT id,
           node_code,
           node_name,
           REPLACE(TO_BASE64(assignee_rule_json), '\\n', ''),
           REPLACE(TO_BASE64(form_rule_json), '\\n', '')
    FROM wf_node_def
    WHERE wf_id = ${Number(workflowId)} AND deleted = 0
    ORDER BY sort_no, id;
  `);
  return parseRows(output, ["id", "node_code", "node_name", "assignee_rule_json", "form_rule_json"]);
}

function updateNode(nodeId, rule) {
  runSql(`UPDATE wf_node_def SET form_rule_json = ${sqlString(JSON.stringify(rule))} WHERE id = ${Number(nodeId)};`);
}

function main() {
  console.log(`表单权限修复脚本: ${mode.replace("--", "")}`);
  console.log(`数据库: ${dbConfig.user}@${dbConfig.host}:${dbConfig.port}/${dbConfig.database}`);

  const { roleNameToCode, roleCodeToName } = loadRoles();
  const formConfigs = loadFormConfigs();
  const workflows = loadWorkflows();
  let changedNodes = 0;

  workflows.forEach((workflow) => {
    const formConfig = formConfigs.get(workflow.form_code);
    if (!formConfig) {
      return;
    }

    console.log(`\n审批流 ${workflow.wf_code} / 表单 ${workflow.form_code}`);
    loadNodes(workflow.id).forEach((node) => {
      const assignee = parseJson(decodeBase64(node.assignee_rule_json), {});
      const assigneeValues = roleValues(assignee.roleName || assignee.roleCode || assignee.role);
      const rule = parseJson(decodeBase64(node.form_rule_json), {});
      rule.fieldAuth ||= {};
      const originalFieldAuth = JSON.parse(JSON.stringify(rule.fieldAuth));
      const matrixLog = [];
      let modified = false;

      for (const [groupName, allowedValues] of formConfig.restrictedGroups.entries()) {
        const editable = canRoleEdit(assigneeValues, allowedValues, roleNameToCode, roleCodeToName);
        const fields = formConfig.fieldsByGroup.get(groupName) || [];
        if (editable) {
          fields.forEach((field) => matrixLog.push(`    ${field}: WRITE 当前节点角色可编辑`));
          continue;
        }
        fields.forEach((field) => {
          modified = setReadonlyIfImplicit(rule, originalFieldAuth, field, matrixLog) || modified;
        });
      }

      const fs = require('fs');
      let customRules = {};
      try {
        customRules = JSON.parse(fs.readFileSync('node_rules.json', 'utf8'));
      } catch (e) {
        // Ignore if file doesn't exist
      }

      const wfRules = customRules[workflow.wf_code];
      if (wfRules && wfRules[node.node_code]) {
        const nodeRules = wfRules[node.node_code];
        const allowedFields = new Set([...(nodeRules.optional || []), ...(nodeRules.required || [])]);
        
        // 1. 将该节点未被显式指派的所有其余字段设为只读
        const allFields = [];
        for (const fields of formConfig.fieldsByGroup.values()) {
          allFields.push(...fields);
        }
        allFields.forEach((field) => {
          if (!allowedFields.has(field)) {
            modified = setReadonlyIfImplicit(rule, originalFieldAuth, field, matrixLog) || modified;
          }
        });

        // 2. 将被指派为可选和必填的字段分别放开权限
        (nodeRules.optional || []).forEach((field) => {
          modified = setRegistrationWritableOptional(rule, field, matrixLog) || modified;
        });
        (nodeRules.required || []).forEach((field) => {
          modified = setRegistrationWritableRequired(rule, originalFieldAuth, field, matrixLog) || modified;
        });
      }

      if (node.node_code === "INIT_FILL" || node.node_code === "CLERK_REGISTER") {
        ENTRUST_REGISTRATION_OPTIONAL_WRITABLE_FIELDS.forEach((field) => {
          modified = setRegistrationWritableOptional(rule, field, matrixLog) || modified;
        });
        ENTRUST_REGISTRATION_REQUIRED_FIELDS.forEach((field) => {
          modified = setRegistrationWritableRequired(rule, originalFieldAuth, field, matrixLog) || modified;
        });
      } else if (workflow.form_code === "received-entrust") {
        ENTRUST_REGISTRATION_BASE_FIELDS.forEach((field) => {
          modified = setReceivedEntrustBaseReadonlyOptional(rule, field, matrixLog) || modified;
        });
      }

      if (matrixLog.length > 0) {
        console.log(`  节点 ${node.node_code} (${node.node_name || "未命名"})`);
        matrixLog.forEach((line) => console.log(line));
      }

      if (modified) {
        changedNodes += 1;
        if (mode === "--apply") {
          updateNode(node.id, rule);
          console.log(`    APPLY 已更新节点 ${node.id}`);
        }
      }
    });
  });

  if (mode === "--verify" && changedNodes > 0) {
    console.error(`\nverify 未通过: 仍有 ${changedNodes} 个节点需要修复。请先执行 --dry-run 检查，再执行 --apply。`);
    process.exit(1);
  }

  console.log(`\n完成。${mode === "--apply" ? "已更新" : "将影响"}节点数: ${changedNodes}`);
}

try {
  main();
} catch (error) {
  console.error(error.message || error);
  process.exit(1);
}
