package com.example.judicialappraisal.contract.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.judicialappraisal.audit.entity.AuditEvent;
import com.example.judicialappraisal.audit.mapper.AuditEventMapper;
import com.example.judicialappraisal.auth.dto.CurrentUserInfo;
import com.example.judicialappraisal.auth.dto.CurrentUserRole;
import com.example.judicialappraisal.contract.dto.ContractCreateRequest;
import com.example.judicialappraisal.contract.dto.ContractResponse;
import com.example.judicialappraisal.contract.dto.ContractReviewRequest;
import com.example.judicialappraisal.contract.dto.ContractUpdateRequest;
import com.example.judicialappraisal.contract.entity.ContractAttachment;
import com.example.judicialappraisal.contract.entity.ContractInfo;
import com.example.judicialappraisal.contract.entity.ContractVersion;
import com.example.judicialappraisal.contract.mapper.ContractAttachmentMapper;
import com.example.judicialappraisal.contract.mapper.ContractInfoMapper;
import com.example.judicialappraisal.contract.mapper.ContractVersionMapper;
import com.example.judicialappraisal.file.entity.SysFile;
import com.example.judicialappraisal.file.mapper.SysFileMapper;
import com.example.judicialappraisal.knowledge.entity.KnowledgeDocument;
import com.example.judicialappraisal.knowledge.mapper.KnowledgeDocumentMapper;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class ContractManagementIntegrationTest {

    @Autowired
    private ContractService contractService;

    @Autowired
    private ContractInfoMapper contractInfoMapper;

    @Autowired
    private ContractVersionMapper contractVersionMapper;

    @Autowired
    private ContractAttachmentMapper contractAttachmentMapper;

    @Autowired
    private SysFileMapper sysFileMapper;

    @Autowired
    private KnowledgeDocumentMapper knowledgeDocumentMapper;

    @Autowired
    private AuditEventMapper auditEventMapper;

    private Long testFileId;

    @BeforeEach
    void setUp() {
        // Create a test file
        SysFile file = new SysFile();
        file.setOriginalName("测试合同附件.pdf");
        file.setFileName("test.pdf");
        file.setFileExt("pdf");
        file.setFileSize(1024L);
        file.setContentType("application/pdf");
        file.setStorageBucket("test-bucket");
        file.setStorageKey("contracts/test.pdf");
        file.setDeleted(0);
        sysFileMapper.insert(file);
        testFileId = file.getId();
    }

    @Test
    void contractShouldCompleteCreateSubmitApproveArchiveFlow() {
        CurrentUserInfo owner = user(100L, "contract_owner", "合同负责人", 20L, "业务部", List.of());
        CurrentUserInfo reviewer = user(200L, "contract_reviewer", "合同审核人", 20L, "业务部", List.of());

        // 1. Create Contract
        ContractCreateRequest createRequest = new ContractCreateRequest(
                "集成测试合同",
                "测试客户",
                888L,
                new BigDecimal("50000.00"),
                20L,
                "业务部",
                "合同初始内容",
                "创建合同",
                List.of(testFileId)
        );
        ContractResponse created = contractService.create(createRequest, owner);
        Long contractId = created.id();

        assertThat(created.status()).isEqualTo("DRAFT");
        assertThat(created.contractNo()).startsWith("HT-");
        assertThat(created.ownerId()).isEqualTo(100L);

        // Verify Version
        ContractVersion v1 = contractVersionMapper.selectOne(new LambdaQueryWrapper<ContractVersion>()
                .eq(ContractVersion::getContractId, contractId)
                .eq(ContractVersion::getVersionNo, 1));
        assertThat(v1).isNotNull();
        assertThat(v1.getContent()).isEqualTo("合同初始内容");

        // Verify Attachment
        ContractAttachment attachment = contractAttachmentMapper.selectOne(new LambdaQueryWrapper<ContractAttachment>()
                .eq(ContractAttachment::getContractId, contractId)
                .eq(ContractAttachment::getFileId, testFileId));
        assertThat(attachment).isNotNull();

        // 2. Update Contract
        ContractUpdateRequest updateRequest = new ContractUpdateRequest(
                "集成测试合同-修订",
                "测试客户-新",
                888L,
                new BigDecimal("55000.00"),
                20L,
                "业务部",
                "合同修订内容",
                "修订金额",
                List.of(testFileId)
        );
        ContractResponse updated = contractService.update(contractId, updateRequest, owner);
        assertThat(updated.status()).isEqualTo("DRAFT");
        assertThat(updated.contractName()).isEqualTo("集成测试合同-修订");

        // Verify Version 2
        ContractVersion v2 = contractVersionMapper.selectOne(new LambdaQueryWrapper<ContractVersion>()
                .eq(ContractVersion::getContractId, contractId)
                .eq(ContractVersion::getVersionNo, 2));
        assertThat(v2).isNotNull();
        assertThat(v2.getContent()).isEqualTo("合同修订内容");

        // 3. Submit Contract
        ContractResponse submitted = contractService.submit(contractId, owner);
        assertThat(submitted.status()).isEqualTo("UNDER_REVIEW");
        assertThat(submitted.submittedAt()).isNotNull();

        // 4. Approve Contract
        ContractReviewRequest reviewRequest = new ContractReviewRequest("审核通过，准予归档");
        ContractResponse approved = contractService.approve(contractId, reviewRequest, reviewer);
        assertThat(approved.status()).isEqualTo("ARCHIVED");
        assertThat(approved.approvedAt()).isNotNull();
        assertThat(approved.archivedAt()).isNotNull();
        assertThat(approved.archiveDocumentId()).isNotNull();

        // 5. Verify Knowledge Archive
        KnowledgeDocument doc = knowledgeDocumentMapper.selectById(approved.archiveDocumentId());
        assertThat(doc).isNotNull();
        assertThat(doc.getArtifactCode()).isEqualTo("CONTRACT-" + contractId);
        assertThat(doc.getTitle()).contains("集成测试合同-修订");

        // 6. Verify Audit Logs
        List<AuditEvent> logs = auditEventMapper.selectList(new LambdaQueryWrapper<AuditEvent>()
                .eq(AuditEvent::getBizType, "contract")
                .eq(AuditEvent::getBizId, contractId)
                .orderByAsc(AuditEvent::getOperatedTime));
        
        assertThat(logs).hasSizeGreaterThanOrEqualTo(4);
        assertThat(logs.stream().map(AuditEvent::getActionCode)).contains(
                "CONTRACT_CREATE", "CONTRACT_UPDATE", "CONTRACT_SUBMIT", "CONTRACT_APPROVE", "CONTRACT_ARCHIVE"
        );
    }

    private CurrentUserInfo user(Long id, String username, String realName, Long deptId, String deptName, List<CurrentUserRole> roles) {
        return new CurrentUserInfo(id, username, realName, null, null, deptId, deptName,
                null, null, "active", roles, Set.of());
    }
}
