package com.example.judicialappraisal.contract.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.judicialappraisal.audit.service.AuditLogService;
import com.example.judicialappraisal.auth.dto.CurrentUserInfo;
import com.example.judicialappraisal.auth.dto.CurrentUserRole;
import com.example.judicialappraisal.common.exception.BusinessException;
import com.example.judicialappraisal.contract.dto.ContractCreateRequest;
import com.example.judicialappraisal.contract.dto.ContractReviewRequest;
import com.example.judicialappraisal.contract.entity.ContractAttachment;
import com.example.judicialappraisal.contract.entity.ContractInfo;
import com.example.judicialappraisal.contract.entity.ContractVersion;
import com.example.judicialappraisal.contract.mapper.ContractAttachmentMapper;
import com.example.judicialappraisal.contract.mapper.ContractInfoMapper;
import com.example.judicialappraisal.contract.mapper.ContractVersionMapper;
import com.example.judicialappraisal.file.entity.SysFile;
import com.example.judicialappraisal.file.mapper.SysFileMapper;
import com.example.judicialappraisal.knowledge.dto.KnowledgeDocumentDto;
import com.example.judicialappraisal.knowledge.service.KnowledgeService;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class ContractServiceTests {

    private final ContractInfoMapper contractInfoMapper = mock(ContractInfoMapper.class);
    private final ContractVersionMapper contractVersionMapper = mock(ContractVersionMapper.class);
    private final ContractAttachmentMapper contractAttachmentMapper = mock(ContractAttachmentMapper.class);
    private final SysFileMapper sysFileMapper = mock(SysFileMapper.class);
    private final KnowledgeService knowledgeService = mock(KnowledgeService.class);
    private final AuditLogService auditLogService = mock(AuditLogService.class);
    private final ContractService service = new ContractService(
            contractInfoMapper,
            contractVersionMapper,
            contractAttachmentMapper,
            sysFileMapper,
            knowledgeService,
            auditLogService
    );

    @Test
    void createContractCreatesDraftVersionAttachmentsAndAudit() {
        stubContractInsert(10L);
        when(contractInfoMapper.selectCount(any())).thenReturn(0L);
        when(contractVersionMapper.selectList(any())).thenReturn(List.of());
        when(contractAttachmentMapper.selectList(any())).thenReturn(List.of());
        SysFile file = new SysFile();
        file.setId(3001L);
        file.setOriginalName("合同正文.pdf");
        file.setDeleted(0);
        when(sysFileMapper.selectById(3001L)).thenReturn(file);

        var response = service.create(new ContractCreateRequest(
                "司法鉴定服务合同",
                "杭州某科技公司",
                88L,
                new BigDecimal("12000.00"),
                20L,
                "鉴定业务部",
                "合同正文内容",
                "首版",
                List.of(3001L)
        ), owner());

        assertThat(response.id()).isEqualTo(10L);
        assertThat(response.contractNo()).startsWith("HT-");
        assertThat(response.status()).isEqualTo("DRAFT");

        ArgumentCaptor<ContractVersion> versionCaptor = ArgumentCaptor.forClass(ContractVersion.class);
        verify(contractVersionMapper).insert(versionCaptor.capture());
        assertThat(versionCaptor.getValue().getVersionNo()).isEqualTo(1);
        assertThat(versionCaptor.getValue().getContent()).isEqualTo("合同正文内容");

        ArgumentCaptor<ContractAttachment> attachmentCaptor = ArgumentCaptor.forClass(ContractAttachment.class);
        verify(contractAttachmentMapper).insert(attachmentCaptor.capture());
        assertThat(attachmentCaptor.getValue().getFileId()).isEqualTo(3001L);
        assertThat(attachmentCaptor.getValue().getFileName()).isEqualTo("合同正文.pdf");
        verify(auditLogService).record(eq("CONTRACT_CREATE"), any(), eq("contract"), eq(10L), eq(88L), any());
    }

    @Test
    void submitContractMovesToDepartmentReview() {
        ContractInfo contract = contract(10L, "DRAFT", 100L, 20L);
        when(contractInfoMapper.selectById(10L)).thenReturn(contract);
        when(contractVersionMapper.selectList(any())).thenReturn(List.of(version(10L)));
        when(contractAttachmentMapper.selectList(any())).thenReturn(List.of());

        var response = service.submit(10L, owner());

        assertThat(response.status()).isEqualTo("UNDER_REVIEW");
        assertThat(contract.getSubmittedAt()).isNotNull();
        verify(contractInfoMapper).updateById(contract);
        verify(auditLogService).record(eq("CONTRACT_SUBMIT"), any(), eq("contract"), eq(10L), eq(88L), any());
    }

    @Test
    void approveContractArchivesToKnowledgeAndRecordsAudit() {
        ContractInfo contract = contract(10L, "UNDER_REVIEW", 100L, 20L);
        ContractAttachment attachment = new ContractAttachment();
        attachment.setId(501L);
        attachment.setContractId(10L);
        attachment.setFileId(3001L);
        attachment.setFileName("合同正文.pdf");
        attachment.setDeleted(0);
        when(contractInfoMapper.selectById(10L)).thenReturn(contract);
        when(contractVersionMapper.selectList(any())).thenReturn(List.of(version(10L)));
        when(contractAttachmentMapper.selectList(any())).thenReturn(List.of(attachment));
        when(knowledgeService.archiveBusinessDocument(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(new KnowledgeDocumentDto(9001L, 5L, null, "合同归档", "CONTRACT-10",
                        "archive", null, null, null, 3001L, 1, "active", LocalDateTime.now()));

        var response = service.approve(10L, new ContractReviewRequest("部门审核通过"), reviewer());

        assertThat(response.status()).isEqualTo("ARCHIVED");
        assertThat(response.archiveDocumentId()).isEqualTo(9001L);
        assertThat(contract.getApprovedAt()).isNotNull();
        assertThat(contract.getArchivedAt()).isNotNull();
        verify(knowledgeService).archiveBusinessDocument(
                eq("contract"),
                eq(10L),
                any(),
                eq("CONTRACT-10"),
                any(Map.class),
                eq(List.of(3001L)),
                eq("合同审批通过并归档"));
        verify(auditLogService).record(eq("CONTRACT_APPROVE"), any(), eq("contract"), eq(10L), eq(88L), any());
        verify(auditLogService).record(eq("CONTRACT_ARCHIVE"), any(), eq("contract"), eq(10L), eq(88L), any());
    }

    @Test
    void userOutsideContractDepartmentCannotApprove() {
        when(contractInfoMapper.selectById(10L)).thenReturn(contract(10L, "UNDER_REVIEW", 100L, 20L));

        assertThatThrownBy(() -> service.approve(10L, new ContractReviewRequest("通过"), outsider()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("所属部门");
    }

    private void stubContractInsert(Long id) {
        doAnswer(invocation -> {
            ContractInfo contract = invocation.getArgument(0);
            contract.setId(id);
            return 1;
        }).when(contractInfoMapper).insert(any(ContractInfo.class));
    }

    private ContractInfo contract(Long id, String status, Long ownerId, Long departmentId) {
        ContractInfo contract = new ContractInfo();
        contract.setId(id);
        contract.setContractNo("HT-2026-0001");
        contract.setContractName("司法鉴定服务合同");
        contract.setCustomerName("杭州某科技公司");
        contract.setRelatedCaseId(88L);
        contract.setAmount(new BigDecimal("12000.00"));
        contract.setOwnerId(ownerId);
        contract.setOwnerName("张三");
        contract.setDepartmentId(departmentId);
        contract.setDepartmentName("鉴定业务部");
        contract.setStatus(status);
        contract.setDeleted(0);
        contract.setCreatedAt(LocalDateTime.now());
        contract.setUpdatedAt(LocalDateTime.now());
        return contract;
    }

    private ContractVersion version(Long contractId) {
        ContractVersion version = new ContractVersion();
        version.setId(2001L);
        version.setContractId(contractId);
        version.setVersionNo(1);
        version.setTitle("司法鉴定服务合同");
        version.setContent("合同正文内容");
        version.setChangeNote("首版");
        version.setCreatedAt(LocalDateTime.now());
        return version;
    }

    private CurrentUserInfo owner() {
        return user(100L, "zhangsan", "张三", 20L, "鉴定业务部", List.of());
    }

    private CurrentUserInfo reviewer() {
        return user(200L, "lisi", "李四", 20L, "鉴定业务部", List.of());
    }

    private CurrentUserInfo outsider() {
        return user(300L, "wangwu", "王五", 99L, "外部部门", List.of());
    }

    private CurrentUserInfo user(Long id,
                                 String username,
                                 String realName,
                                 Long deptId,
                                 String deptName,
                                 List<CurrentUserRole> roles) {
        return new CurrentUserInfo(id, username, realName, null, null, deptId, deptName,
                null, null, "active", roles, Set.of());
    }
}
