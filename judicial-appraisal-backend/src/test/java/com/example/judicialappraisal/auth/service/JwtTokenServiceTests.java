package com.example.judicialappraisal.auth.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.judicialappraisal.auth.dto.CurrentUserInfo;
import com.example.judicialappraisal.auth.dto.CurrentUserRole;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class JwtTokenServiceTests {

    @Test
    void preservesDepartmentRolesDataScopesAndPermissions() {
        JwtTokenService service = new JwtTokenService(
                "test-secret-test-secret-test-secret-test-secret",
                "test-issuer",
                60
        );
        CurrentUserInfo source = new CurrentUserInfo(
                9L,
                "reviewer",
                "审核员",
                null,
                null,
                12L,
                "技术部",
                null,
                null,
                "enabled",
                List.of(new CurrentUserRole(5L, "REVIEWER", "审核员", "dept_sub", List.of(12L, 13L))),
                Set.of("workflow:review")
        );

        CurrentUserInfo parsed = service.parseToken(service.generateToken(source));

        assertThat(parsed.id()).isEqualTo(9L);
        assertThat(parsed.deptId()).isEqualTo(12L);
        assertThat(parsed.roles()).containsExactly(new CurrentUserRole(5L, "REVIEWER", "审核员", "dept_sub", List.of(12L, 13L)));
        assertThat(parsed.permissions()).containsExactly("workflow:review");
    }
}
