package com.example.judicialappraisal.caseinfo.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.judicialappraisal.auth.dto.CurrentUserInfo;
import com.example.judicialappraisal.auth.dto.CurrentUserRole;
import com.example.judicialappraisal.caseinfo.dto.CaseFormDataSaveRequest;
import com.example.judicialappraisal.caseinfo.entity.CaseInfo;
import com.example.judicialappraisal.caseinfo.mapper.CaseInfoMapper;
import com.example.judicialappraisal.common.enums.CaseStatus;
import com.example.judicialappraisal.common.exception.BusinessException;
import com.example.judicialappraisal.workflow.entity.CaseTask;
import com.example.judicialappraisal.workflow.mapper.CaseTaskCandidateMapper;
import com.example.judicialappraisal.workflow.mapper.CaseTaskMapper;
import com.example.judicialappraisal.workflow.service.WorkflowRuntimeService;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class CaseInfoServiceTests {

    private final WorkflowRuntimeService workflowRuntimeService = mock(WorkflowRuntimeService.class);
    private final CaseTaskMapper caseTaskMapper = mock(CaseTaskMapper.class);
    private final CaseTaskCandidateMapper caseTaskCandidateMapper = mock(CaseTaskCandidateMapper.class);
    private final CaseInfoMapper caseInfoMapper = mock(CaseInfoMapper.class);
    private final CaseInfoService service = new CaseInfoService(workflowRuntimeService, caseTaskMapper, caseTaskCandidateMapper);

    CaseInfoServiceTests() {
        ReflectionTestUtils.setField(service, "baseMapper", caseInfoMapper);
    }

    @Test
    void draftCreatorCanSaveFormDataWithoutStartingWorkflow() {
        CaseInfo caseInfo = caseInfo(18L, CaseStatus.DRAFT.name(), 7L, null);
        when(caseInfoMapper.selectRawById(18L)).thenReturn(caseInfo);

        CaseInfo saved = service.saveFormData(18L, new CaseFormDataSaveRequest(Map.of(
                "caseNo", "JA-20260626-001",
                "caseTitle", "收到委托书验收",
                "entrustOrgName", "测试委托单位",
                "accepted", true
        ), "先保存"), user(7L, "case_acceptor", 2L));

        assertThat(saved.getFormData())
                .containsEntry("caseNo", "JA-20260626-001")
                .containsEntry("accepted", true);
        assertThat(saved.getCaseNo()).isEqualTo("JA-20260626-001");
        assertThat(saved.getCaseTitle()).isEqualTo("收到委托书验收");
        assertThat(saved.getEntrustOrgName()).isEqualTo("测试委托单位");
        verify(caseInfoMapper).updateById(caseInfo);
    }

    @Test
    void candidateUserCanSaveActiveTaskFormData() {
        CaseInfo caseInfo = caseInfo(19L, CaseStatus.TO_ACCEPT.name(), 3L, null);
        CaseTask task = new CaseTask();
        task.setId(91L);
        task.setCaseId(19L);
        task.setStatus("pending");

        when(caseInfoMapper.selectRawById(19L)).thenReturn(caseInfo);
        when(caseTaskMapper.selectList(any())).thenReturn(List.of(task));
        when(caseTaskCandidateMapper.selectCount(any())).thenReturn(1L);

        CaseInfo saved = service.saveFormData(19L, new CaseFormDataSaveRequest(Map.of(
                "clerkRegistered", true
        ), "候选人保存"), user(8L, "case_acceptor", 2L));

        assertThat(saved.getFormData()).containsEntry("clerkRegistered", true);
        verify(caseInfoMapper).updateById(caseInfo);
    }

    @Test
    void unrelatedUserCannotSaveActiveCaseFormData() {
        CaseInfo caseInfo = caseInfo(20L, CaseStatus.TO_ACCEPT.name(), 3L, null);
        CaseTask task = new CaseTask();
        task.setId(92L);
        task.setCaseId(20L);
        task.setStatus("pending");

        when(caseInfoMapper.selectRawById(20L)).thenReturn(caseInfo);
        when(caseTaskMapper.selectList(any())).thenReturn(List.of(task));
        when(caseTaskCandidateMapper.selectCount(any())).thenReturn(0L);

        assertThatThrownBy(() -> service.saveFormData(20L,
                new CaseFormDataSaveRequest(Map.of("accepted", false), "无权保存"),
                user(99L, "finance", 9L)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("无权保存");
    }

    private CaseInfo caseInfo(Long id, String status, Long createdBy, Long currentHandlerId) {
        CaseInfo value = new CaseInfo();
        value.setId(id);
        value.setCaseTitle("收到委托书");
        value.setCaseStatus(status);
        value.setCreatedBy(createdBy);
        value.setCurrentHandlerId(currentHandlerId);
        value.setDeleted(0);
        return value;
    }

    private CurrentUserInfo user(Long id, String roleCode, Long roleId) {
        return new CurrentUserInfo(
                id,
                "user" + id,
                "用户" + id,
                null,
                null,
                null,
                null,
                null,
                null,
                "enabled",
                List.of(new CurrentUserRole(roleId, roleCode, roleCode, "SELF", List.of())),
                Set.of()
        );
    }
}
