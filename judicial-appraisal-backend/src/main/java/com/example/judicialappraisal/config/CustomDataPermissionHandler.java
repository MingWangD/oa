package com.example.judicialappraisal.config;

import com.baomidou.mybatisplus.extension.plugins.handler.MultiDataPermissionHandler;
import com.example.judicialappraisal.auth.dto.CurrentUserInfo;
import com.example.judicialappraisal.auth.dto.CurrentUserRole;
import java.util.Locale;
import java.util.List;
import java.util.stream.Collectors;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.LongValue;
import net.sf.jsqlparser.expression.Parenthesis;
import net.sf.jsqlparser.expression.operators.relational.EqualsTo;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.JSQLParserException;
import net.sf.jsqlparser.schema.Column;
import net.sf.jsqlparser.schema.Table;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
public class CustomDataPermissionHandler implements MultiDataPermissionHandler {

    private static final String CASE_INFO_TABLE = "case_info";

    @Override
    public Expression getSqlSegment(Table table, Expression where, String mappedStatementId) {
        if (table == null || !CASE_INFO_TABLE.equalsIgnoreCase(table.getName())) {
            return null;
        }

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof CurrentUserInfo userInfo)) {
            return null;
        }

        DataScopeLevel scope = resolveScope(userInfo);
        if (scope == DataScopeLevel.ALL) {
            return null;
        }

        Expression scopeExpression = switch (scope) {
            case CUSTOM -> customDepartments(table, userInfo);
            case DEPT_SUB -> departmentAndSubDepartments(table, userInfo.deptId());
            case DEPT -> equalsTo(table, "accept_dept_id", userInfo.deptId());
            case SELF -> selfCases(table, userInfo.id());
            case ALL -> null;
        };
        Expression workflowAccessExpression = workflowParticipantCases(table, userInfo);
        if (scopeExpression == null) {
            return workflowAccessExpression == null ? denyAll() : workflowAccessExpression;
        }
        return or(scopeExpression, workflowAccessExpression);
    }

    DataScopeLevel resolveScope(CurrentUserInfo userInfo) {
        DataScopeLevel result = DataScopeLevel.SELF;
        for (CurrentUserRole role : userInfo.roles()) {
            if ("ADMIN".equalsIgnoreCase(role.code())) {
                return DataScopeLevel.ALL;
            }
            DataScopeLevel roleScope = DataScopeLevel.from(role.dataScope());
            if (roleScope.priority < result.priority) {
                result = roleScope;
            }
        }
        return result;
    }

    private Expression customDepartments(Table table, CurrentUserInfo userInfo) {
        List<Long> customDeptIds = userInfo.roles().stream()
                .filter(role -> DataScopeLevel.CUSTOM.matches(role.dataScope()))
                .flatMap(role -> role.customDeptIds().stream())
                .distinct()
                .toList();
        if (customDeptIds.isEmpty()) {
            return null;
        }
        String ids = customDeptIds.stream().map(String::valueOf).collect(Collectors.joining(", "));
        String condition = qualifiedColumn(table, "accept_dept_id") + " IN (" + ids + ")";
        try {
            return CCJSqlParserUtil.parseCondExpression(condition);
        } catch (JSQLParserException ex) {
            throw new IllegalStateException("Failed to build custom data scope", ex);
        }
    }

    private Expression equalsTo(Table table, String columnName, Long value) {
        if (value == null) {
            return null;
        }
        EqualsTo expression = new EqualsTo();
        expression.setLeftExpression(new Column(qualifiedColumn(table, columnName)));
        expression.setRightExpression(new LongValue(value));
        return expression;
    }

    private Expression selfCases(Table table, Long userId) {
        if (userId == null) {
            return null;
        }
        String condition = "(" + qualifiedColumn(table, "current_handler_id") + " = " + userId
                + " OR " + qualifiedColumn(table, "created_by") + " = " + userId + ")";
        try {
            return CCJSqlParserUtil.parseCondExpression(condition);
        } catch (JSQLParserException ex) {
            throw new IllegalStateException("Failed to build self data scope", ex);
        }
    }

    private Expression workflowParticipantCases(Table table, CurrentUserInfo userInfo) {
        if (userInfo.id() == null) {
            return null;
        }
        String caseIdColumn = qualifiedColumn(table, "id");
        String roleIds = userInfo.roles().stream()
                .map(CurrentUserRole::id)
                .filter(id -> id != null)
                .map(String::valueOf)
                .collect(Collectors.joining(", "));
        String candidateRoleCondition = roleIds.isBlank() ? "1 = 0" : "ctc.candidate_role_id IN (" + roleIds + ")";
        String condition = "EXISTS (SELECT 1 FROM case_task ct WHERE ct.case_id = " + caseIdColumn
                + " AND (ct.assignee_id = " + userInfo.id() + " OR ct.claimed_by = " + userInfo.id() + "))"
                + " OR EXISTS (SELECT 1 FROM case_task_candidate ctc WHERE ctc.case_id = " + caseIdColumn
                + " AND (ctc.candidate_user_id = " + userInfo.id() + " OR " + candidateRoleCondition + "))";
        try {
            return CCJSqlParserUtil.parseCondExpression(condition);
        } catch (JSQLParserException ex) {
            throw new IllegalStateException("Failed to build workflow participant data scope", ex);
        }
    }

    private Expression or(Expression left, Expression right) {
        if (right == null) {
            return left;
        }
        try {
            return new Parenthesis(CCJSqlParserUtil.parseCondExpression("(" + left + ") OR (" + right + ")"));
        } catch (JSQLParserException ex) {
            throw new IllegalStateException("Failed to build data scope expression", ex);
        }
    }

    private Expression denyAll() {
        EqualsTo expression = new EqualsTo();
        expression.setLeftExpression(new LongValue(1));
        expression.setRightExpression(new LongValue(0));
        return expression;
    }

    private Expression departmentAndSubDepartments(Table table, Long deptId) {
        if (deptId == null) {
            return null;
        }
        String column = qualifiedColumn(table, "accept_dept_id");
        String condition = column + " IN ("
                + "WITH RECURSIVE dept_tree AS ("
                + "SELECT id FROM sys_dept WHERE id = " + deptId + " AND deleted = 0 "
                + "UNION ALL "
                + "SELECT d.id FROM sys_dept d INNER JOIN dept_tree dt ON d.parent_id = dt.id WHERE d.deleted = 0"
                + ") SELECT id FROM dept_tree)";
        try {
            return CCJSqlParserUtil.parseCondExpression(condition);
        } catch (JSQLParserException ex) {
            throw new IllegalStateException("Failed to build department data scope", ex);
        }
    }

    private String qualifiedColumn(Table table, String columnName) {
        if (table.getAlias() != null && table.getAlias().getName() != null) {
            return table.getAlias().getName() + "." + columnName;
        }
        return table.getName() + "." + columnName;
    }

    enum DataScopeLevel {
        ALL(1),
        DEPT_SUB(2),
        CUSTOM(3),
        DEPT(4),
        SELF(5);

        private final int priority;

        DataScopeLevel(int priority) {
            this.priority = priority;
        }

        static DataScopeLevel from(String value) {
            if (value == null || value.isBlank()) {
                return SELF;
            }
            return switch (value.toLowerCase(Locale.ROOT)) {
                case "all" -> ALL;
                case "dept_sub" -> DEPT_SUB;
                case "custom" -> CUSTOM;
                case "dept" -> DEPT;
                default -> SELF;
            };
        }

        boolean matches(String value) {
            return from(value) == this;
        }
    }
}
