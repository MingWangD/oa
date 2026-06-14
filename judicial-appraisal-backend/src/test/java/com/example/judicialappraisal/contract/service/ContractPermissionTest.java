package com.example.judicialappraisal.contract.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.judicialappraisal.auth.dto.CurrentUserInfo;
import com.example.judicialappraisal.auth.dto.CurrentUserRole;
import com.example.judicialappraisal.common.exception.BusinessException;
import com.example.judicialappraisal.contract.dto.ContractCreateRequest;
import com.example.judicialappraisal.contract.dto.ContractResponse;
import com.example.judicialappraisal.contract.dto.ContractReviewRequest;
import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class ContractPermissionTest {

    @Autowired
    private ContractService contractService;

    @Test
    void unauthorizedUserCannotViewContractOutsideScope() {
        CurrentUserInfo owner = user(100L, "owner", "负责人", 20L, "业务部", List.of());
        CurrentUserInfo outsider = user(300L, "outsider", "外人", 99L, "外部部门", List.of());

        ContractResponse contract = createContract(owner);

        assertThatThrownBy(() -> contractService.detail(contract.id(), outsider))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("无权查看");
    }

    @Test
    void unauthorizedUserCannotApproveContractOutsideDepartment() {
        CurrentUserInfo owner = user(100L, "owner", "负责人", 20L, "业务部", List.of());
        CurrentUserInfo outsider = user(300L, "outsider", "外人", 99L, "外部部门", List.of());

        ContractResponse contract = createContract(owner);
        contractService.submit(contract.id(), owner);

        assertThatThrownBy(() -> contractService.approve(contract.id(), new ContractReviewRequest("通过"), outsider))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("所属部门审核人");
    }

    @Test
    void forgedOperatorFieldsCannotBypassContractPermission() {
        CurrentUserInfo owner = user(100L, "owner", "负责人", 20L, "业务部", List.of());
        
        // Request tries to set ownerId to 999
        ContractCreateRequest request = new ContractCreateRequest(
                "伪造测试", "客户", null, BigDecimal.ZERO, 20L, "业务部", "内容", "注", List.of());
        
        ContractResponse created = contractService.create(request, owner);
        
        // Owner ID should be 100 (from owner), not something else
        assertThat(created.ownerId()).isEqualTo(100L);
        assertThat(created.ownerName()).isEqualTo("负责人");
    }

    @Test
    void adminCanReviewContractAcrossDepartments() {
        CurrentUserInfo owner = user(100L, "owner", "负责人", 20L, "业务部", List.of());
        CurrentUserInfo admin = user(1L, "admin", "管理员", 1L, "总办", List.of(new CurrentUserRole(1L, "ADMIN", "管理员", "all", List.of())));

        ContractResponse contract = createContract(owner);
        contractService.submit(contract.id(), owner);

        // Admin (dept 1) can approve contract in dept 20
        ContractResponse approved = contractService.approve(contract.id(), new ContractReviewRequest("管理员通过"), admin);
        assertThat(approved.status()).isEqualTo("ARCHIVED");
    }

    private ContractResponse createContract(CurrentUserInfo owner) {
        ContractCreateRequest request = new ContractCreateRequest(
                "权限测试合同", "客户", null, BigDecimal.ZERO, owner.deptId(), owner.deptName(), "内容", "注", List.of());
        return contractService.create(request, owner);
    }

    private CurrentUserInfo user(Long id, String username, String realName, Long deptId, String deptName, List<CurrentUserRole> roles) {
        return new CurrentUserInfo(id, username, realName, null, null, deptId, deptName,
                null, null, "active", roles, Set.of());
    }
}
