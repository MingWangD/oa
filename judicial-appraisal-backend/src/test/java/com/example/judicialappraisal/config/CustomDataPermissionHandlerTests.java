package com.example.judicialappraisal.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.judicialappraisal.auth.dto.CurrentUserInfo;
import com.example.judicialappraisal.auth.dto.CurrentUserRole;
import java.util.List;
import java.util.Set;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.schema.Table;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

class CustomDataPermissionHandlerTests {

    private final CustomDataPermissionHandler handler = new CustomDataPermissionHandler();

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void ignoresTablesOutsideExplicitBusinessScope() {
        authenticate(user(7L, 3L, "self", "USER"));

        Expression expression = handler.getSqlSegment(new Table("sys_user_role"), null, "selectUsers");

        assertThat(expression).isNull();
    }

    @Test
    void appliesSelfScopeToCurrentHandler() {
        authenticate(user(7L, 3L, "self", "USER"));

        Expression expression = handler.getSqlSegment(new Table("case_info"), null, "selectCases");

        assertThat(expression.toString()).isEqualTo("case_info.current_handler_id = 7");
    }

    @Test
    void appliesDepartmentScopeToAcceptDepartment() {
        authenticate(user(7L, 3L, "dept", "USER"));

        Expression expression = handler.getSqlSegment(new Table("case_info"), null, "selectCases");

        assertThat(expression.toString()).isEqualTo("case_info.accept_dept_id = 3");
    }

    @Test
    void appliesRecursiveDepartmentScopeForDepartmentAndChildren() {
        authenticate(user(7L, 3L, "dept_sub", "USER"));

        Expression expression = handler.getSqlSegment(new Table("case_info"), null, "selectCases");

        assertThat(expression.toString())
                .contains("case_info.accept_dept_id IN")
                .contains("WITH RECURSIVE dept_tree")
                .contains("parent_id = dt.id");
    }

    @Test
    void adminRoleHasAllDataScopeRegardlessOfRoleId() {
        authenticate(user(7L, 3L, "self", "ADMIN"));

        Expression expression = handler.getSqlSegment(new Table("case_info"), null, "selectCases");

        assertThat(expression).isNull();
    }

    @Test
    void appliesCustomDepartmentScope() {
        authenticate(new CurrentUserInfo(
                7L,
                "user7",
                "User 7",
                null,
                null,
                3L,
                null,
                null,
                null,
                "enabled",
                List.of(new CurrentUserRole(8L, "CUSTOM_ROLE", "自定义角色", "custom", List.of(12L, 15L))),
                Set.of()
        ));

        Expression expression = handler.getSqlSegment(new Table("case_info"), null, "selectCases");

        assertThat(expression.toString()).isEqualTo("case_info.accept_dept_id IN (12, 15)");
    }

    private void authenticate(CurrentUserInfo userInfo) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(userInfo, "token", List.of())
        );
    }

    private CurrentUserInfo user(Long id, Long deptId, String dataScope, String roleCode) {
        return new CurrentUserInfo(
                id,
                "user" + id,
                "User " + id,
                null,
                null,
                deptId,
                null,
                null,
                null,
                "enabled",
                List.of(new CurrentUserRole(1L, roleCode, roleCode, dataScope, List.of())),
                Set.of()
        );
    }
}
