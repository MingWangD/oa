package com.example.judicialappraisal.contract.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.judicialappraisal.auth.dto.CurrentUserInfo;
import com.example.judicialappraisal.auth.dto.CurrentUserRole;
import com.example.judicialappraisal.common.PageResult;
import com.example.judicialappraisal.common.exception.BusinessException;
import com.example.judicialappraisal.contract.dto.ContractAttachmentDto;
import com.example.judicialappraisal.contract.dto.ContractCreateRequest;
import com.example.judicialappraisal.contract.dto.ContractQueryRequest;
import com.example.judicialappraisal.contract.dto.ContractResponse;
import com.example.judicialappraisal.contract.dto.ContractReviewRequest;
import com.example.judicialappraisal.contract.dto.ContractUpdateRequest;
import com.example.judicialappraisal.contract.dto.ContractVersionDto;
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
import com.example.judicialappraisal.audit.service.AuditLogService;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ContractService {

    private static final String STATUS_DRAFT = "DRAFT";
    private static final String STATUS_UNDER_REVIEW = "UNDER_REVIEW";
    private static final String STATUS_REJECTED = "REJECTED";
    private static final String STATUS_ARCHIVED = "ARCHIVED";

    private final ContractInfoMapper contractInfoMapper;
    private final ContractVersionMapper contractVersionMapper;
    private final ContractAttachmentMapper contractAttachmentMapper;
    private final SysFileMapper sysFileMapper;
    private final KnowledgeService knowledgeService;
    private final AuditLogService auditLogService;

    public ContractService(ContractInfoMapper contractInfoMapper,
                           ContractVersionMapper contractVersionMapper,
                           ContractAttachmentMapper contractAttachmentMapper,
                           SysFileMapper sysFileMapper,
                           KnowledgeService knowledgeService,
                           AuditLogService auditLogService) {
        this.contractInfoMapper = contractInfoMapper;
        this.contractVersionMapper = contractVersionMapper;
        this.contractAttachmentMapper = contractAttachmentMapper;
        this.sysFileMapper = sysFileMapper;
        this.knowledgeService = knowledgeService;
        this.auditLogService = auditLogService;
    }

    public PageResult<ContractResponse> page(ContractQueryRequest request, CurrentUserInfo currentUser) {
        int pageNo = request.pageNo() == null || request.pageNo() <= 0 ? 1 : request.pageNo();
        int pageSize = request.pageSize() == null || request.pageSize() <= 0 ? 10 : Math.min(request.pageSize(), 100);
        LambdaQueryWrapper<ContractInfo> query = new LambdaQueryWrapper<ContractInfo>()
                .eq(ContractInfo::getDeleted, 0)
                .eq(hasText(request.status()) && !"all".equalsIgnoreCase(request.status()), ContractInfo::getStatus, request.status())
                .eq(request.relatedCaseId() != null, ContractInfo::getRelatedCaseId, request.relatedCaseId())
                .and(hasText(request.keyword()), wrapper -> wrapper
                        .like(ContractInfo::getContractNo, request.keyword())
                        .or()
                        .like(ContractInfo::getContractName, request.keyword())
                        .or()
                        .like(ContractInfo::getCustomerName, request.keyword()))
                .orderByDesc(ContractInfo::getUpdatedAt)
                .orderByDesc(ContractInfo::getId);
        applyDataScope(query, currentUser);
        Page<ContractInfo> page = contractInfoMapper.selectPage(Page.of(pageNo, pageSize), query);
        return new PageResult<>(
                page.getRecords().stream().map(this::toResponse).toList(),
                page.getTotal(),
                page.getCurrent(),
                page.getSize());
    }

    public ContractResponse detail(Long contractId, CurrentUserInfo currentUser) {
        ContractInfo contract = requireContract(contractId);
        requireReadable(contract, currentUser);
        return toResponse(contract);
    }

    @Transactional
    public ContractResponse create(ContractCreateRequest request, CurrentUserInfo currentUser) {
        LocalDateTime now = LocalDateTime.now();
        ContractInfo contract = new ContractInfo();
        contract.setContractNo(nextContractNo(now));
        contract.setContractName(request.contractName());
        contract.setCustomerName(request.customerName());
        contract.setRelatedCaseId(request.relatedCaseId());
        contract.setAmount(normalizeAmount(request.amount()));
        contract.setOwnerId(currentUser.id());
        contract.setOwnerName(displayName(currentUser));
        contract.setDepartmentId(request.departmentId() == null ? currentUser.deptId() : request.departmentId());
        contract.setDepartmentName(hasText(request.departmentName()) ? request.departmentName() : currentUser.deptName());
        contract.setStatus(STATUS_DRAFT);
        contract.setCreatedBy(currentUser.id());
        contract.setUpdatedBy(currentUser.id());
        contract.setCreatedAt(now);
        contract.setUpdatedAt(now);
        contract.setDeleted(0);
        contractInfoMapper.insert(contract);

        createVersion(contract.getId(), request.contractName(), request.content(), firstText(request.changeNote(), "合同创建"), currentUser.id(), now);
        replaceAttachments(contract.getId(), request.fileIds(), currentUser.id(), now);
        auditLogService.record("CONTRACT_CREATE", "合同创建", "contract", contract.getId(), request.relatedCaseId(), detailJson(contract, "创建合同草稿"));
        return toResponse(contract);
    }

    @Transactional
    public ContractResponse update(Long contractId, ContractUpdateRequest request, CurrentUserInfo currentUser) {
        ContractInfo contract = requireContract(contractId);
        requireOwnerOrAdmin(contract, currentUser);
        if (!List.of(STATUS_DRAFT, STATUS_REJECTED).contains(contract.getStatus())) {
            throw new BusinessException("只有草稿或已驳回合同允许修改");
        }
        LocalDateTime now = LocalDateTime.now();
        contract.setContractName(request.contractName());
        contract.setCustomerName(request.customerName());
        contract.setRelatedCaseId(request.relatedCaseId());
        contract.setAmount(normalizeAmount(request.amount()));
        contract.setDepartmentId(request.departmentId() == null ? currentUser.deptId() : request.departmentId());
        contract.setDepartmentName(hasText(request.departmentName()) ? request.departmentName() : currentUser.deptName());
        contract.setStatus(STATUS_DRAFT);
        contract.setUpdatedBy(currentUser.id());
        contract.setUpdatedAt(now);
        contractInfoMapper.updateById(contract);

        createVersion(contract.getId(), request.contractName(), request.content(), firstText(request.changeNote(), "合同修改"), currentUser.id(), now);
        replaceAttachments(contract.getId(), request.fileIds(), currentUser.id(), now);
        auditLogService.record("CONTRACT_UPDATE", "合同修改", "contract", contract.getId(), request.relatedCaseId(), detailJson(contract, "修改合同草稿"));
        return toResponse(contract);
    }

    @Transactional
    public ContractResponse submit(Long contractId, CurrentUserInfo currentUser) {
        ContractInfo contract = requireContract(contractId);
        requireOwnerOrAdmin(contract, currentUser);
        if (!List.of(STATUS_DRAFT, STATUS_REJECTED).contains(contract.getStatus())) {
            throw new BusinessException("只有草稿或已驳回合同允许提交审批");
        }
        if (latestVersion(contract.getId()) == null) {
            throw new BusinessException("合同缺少内容版本，不能提交审批");
        }
        LocalDateTime now = LocalDateTime.now();
        contract.setStatus(STATUS_UNDER_REVIEW);
        contract.setSubmittedAt(now);
        contract.setUpdatedBy(currentUser.id());
        contract.setUpdatedAt(now);
        contractInfoMapper.updateById(contract);
        auditLogService.record("CONTRACT_SUBMIT", "合同提交审批", "contract", contract.getId(), contract.getRelatedCaseId(), detailJson(contract, "提交部门审核"));
        return toResponse(contract);
    }

    @Transactional
    public ContractResponse approve(Long contractId, ContractReviewRequest request, CurrentUserInfo currentUser) {
        ContractInfo contract = requireContract(contractId);
        requireReviewer(contract, currentUser);
        if (!STATUS_UNDER_REVIEW.equals(contract.getStatus())) {
            throw new BusinessException("只有审核中的合同允许审批通过");
        }
        LocalDateTime now = LocalDateTime.now();
        ContractVersion version = latestVersion(contract.getId());
        List<Long> fileIds = activeAttachments(contract.getId()).stream().map(ContractAttachment::getFileId).toList();
        Map<String, Object> formData = new LinkedHashMap<>();
        formData.put("contractNo", contract.getContractNo());
        formData.put("contractName", contract.getContractName());
        formData.put("customerName", contract.getCustomerName());
        formData.put("amount", contract.getAmount());
        formData.put("reviewOpinion", request.opinion());
        formData.put("content", version == null ? "" : version.getContent());
        KnowledgeDocumentDto document = knowledgeService.archiveBusinessDocument(
                "contract",
                contract.getId(),
                contract.getContractNo() + " " + contract.getContractName(),
                "CONTRACT-" + contract.getId(),
                formData,
                fileIds,
                "合同审批通过并归档");

        contract.setStatus(STATUS_ARCHIVED);
        contract.setArchiveDocumentId(document.id());
        contract.setReviewOpinion(request.opinion());
        contract.setApprovedAt(now);
        contract.setArchivedAt(now);
        contract.setUpdatedBy(currentUser.id());
        contract.setUpdatedAt(now);
        contractInfoMapper.updateById(contract);

        auditLogService.record("CONTRACT_APPROVE", "合同部门审核通过", "contract", contract.getId(), contract.getRelatedCaseId(), detailJson(contract, firstText(request.opinion(), "审批通过")));
        auditLogService.record("CONTRACT_ARCHIVE", "合同归档知识库", "contract", contract.getId(), contract.getRelatedCaseId(),
                "{\"archiveDocumentId\":" + document.id() + ",\"contractNo\":\"" + escape(contract.getContractNo()) + "\"}");
        return toResponse(contract);
    }

    @Transactional
    public ContractResponse reject(Long contractId, ContractReviewRequest request, CurrentUserInfo currentUser) {
        ContractInfo contract = requireContract(contractId);
        requireReviewer(contract, currentUser);
        if (!STATUS_UNDER_REVIEW.equals(contract.getStatus())) {
            throw new BusinessException("只有审核中的合同允许驳回");
        }
        LocalDateTime now = LocalDateTime.now();
        contract.setStatus(STATUS_REJECTED);
        contract.setReviewOpinion(firstText(request.opinion(), "驳回"));
        contract.setUpdatedBy(currentUser.id());
        contract.setUpdatedAt(now);
        contractInfoMapper.updateById(contract);
        auditLogService.record("CONTRACT_REJECT", "合同部门审核驳回", "contract", contract.getId(), contract.getRelatedCaseId(), detailJson(contract, contract.getReviewOpinion()));
        return toResponse(contract);
    }

    private void createVersion(Long contractId, String title, String content, String changeNote, Long createdBy, LocalDateTime now) {
        Integer latest = contractVersionMapper.selectList(new LambdaQueryWrapper<ContractVersion>()
                        .eq(ContractVersion::getContractId, contractId)
                        .orderByDesc(ContractVersion::getVersionNo)
                        .last("limit 1"))
                .stream()
                .findFirst()
                .map(ContractVersion::getVersionNo)
                .orElse(0);
        ContractVersion version = new ContractVersion();
        version.setContractId(contractId);
        version.setVersionNo(latest + 1);
        version.setTitle(title);
        version.setContent(content);
        version.setChangeNote(changeNote);
        version.setCreatedBy(createdBy);
        version.setCreatedAt(now);
        contractVersionMapper.insert(version);
    }

    private void replaceAttachments(Long contractId, List<Long> fileIds, Long currentUserId, LocalDateTime now) {
        contractAttachmentMapper.delete(new LambdaQueryWrapper<ContractAttachment>()
                .eq(ContractAttachment::getContractId, contractId));
        if (fileIds == null || fileIds.isEmpty()) {
            return;
        }
        for (Long fileId : fileIds.stream().filter(id -> id != null).distinct().toList()) {
            SysFile file = sysFileMapper.selectById(fileId);
            if (file == null || Integer.valueOf(1).equals(file.getDeleted())) {
                throw new BusinessException("合同附件不存在：" + fileId);
            }
            ContractAttachment attachment = new ContractAttachment();
            attachment.setContractId(contractId);
            attachment.setFileId(fileId);
            attachment.setFileName(file.getOriginalName());
            attachment.setArtifactCode("CONTRACT_ATTACHMENT");
            attachment.setCreatedBy(currentUserId);
            attachment.setCreatedAt(now);
            attachment.setDeleted(0);
            contractAttachmentMapper.insert(attachment);
        }
    }

    private ContractInfo requireContract(Long contractId) {
        ContractInfo contract = contractInfoMapper.selectById(contractId);
        if (contract == null || Integer.valueOf(1).equals(contract.getDeleted())) {
            throw new BusinessException("合同不存在");
        }
        return contract;
    }

    private void applyDataScope(LambdaQueryWrapper<ContractInfo> query, CurrentUserInfo currentUser) {
        if (isAdmin(currentUser)) {
            return;
        }
        query.and(wrapper -> {
            wrapper.eq(ContractInfo::getOwnerId, currentUser.id());
            if (currentUser.deptId() != null) {
                wrapper.or().eq(ContractInfo::getDepartmentId, currentUser.deptId());
            }
        });
    }

    private void requireReadable(ContractInfo contract, CurrentUserInfo currentUser) {
        if (isAdmin(currentUser) || currentUser.id().equals(contract.getOwnerId())
                || (currentUser.deptId() != null && currentUser.deptId().equals(contract.getDepartmentId()))) {
            return;
        }
        throw new BusinessException("无权查看该合同");
    }

    private void requireOwnerOrAdmin(ContractInfo contract, CurrentUserInfo currentUser) {
        if (isAdmin(currentUser) || currentUser.id().equals(contract.getOwnerId())) {
            return;
        }
        throw new BusinessException("只有合同负责人可以执行该操作");
    }

    private void requireReviewer(ContractInfo contract, CurrentUserInfo currentUser) {
        if (isAdmin(currentUser) || (currentUser.deptId() != null && currentUser.deptId().equals(contract.getDepartmentId()))) {
            return;
        }
        throw new BusinessException("只有合同所属部门审核人可以审批");
    }

    private boolean isAdmin(CurrentUserInfo currentUser) {
        return currentUser.roles().stream().map(CurrentUserRole::code).anyMatch(code -> "ADMIN".equalsIgnoreCase(code));
    }

    private ContractResponse toResponse(ContractInfo contract) {
        return new ContractResponse(
                contract.getId(),
                contract.getContractNo(),
                contract.getContractName(),
                contract.getCustomerName(),
                contract.getRelatedCaseId(),
                contract.getAmount(),
                contract.getOwnerId(),
                contract.getOwnerName(),
                contract.getDepartmentId(),
                contract.getDepartmentName(),
                contract.getStatus(),
                statusName(contract.getStatus()),
                contract.getArchiveDocumentId(),
                contract.getReviewOpinion(),
                contract.getSubmittedAt(),
                contract.getApprovedAt(),
                contract.getArchivedAt(),
                contract.getCreatedAt(),
                contract.getUpdatedAt(),
                toVersionDto(latestVersion(contract.getId())),
                activeAttachments(contract.getId()).stream().map(this::toAttachmentDto).toList());
    }

    private ContractVersion latestVersion(Long contractId) {
        return contractVersionMapper.selectList(new LambdaQueryWrapper<ContractVersion>()
                        .eq(ContractVersion::getContractId, contractId)
                        .orderByDesc(ContractVersion::getVersionNo)
                        .last("limit 1"))
                .stream()
                .findFirst()
                .orElse(null);
    }

    private List<ContractAttachment> activeAttachments(Long contractId) {
        return contractAttachmentMapper.selectList(new LambdaQueryWrapper<ContractAttachment>()
                .eq(ContractAttachment::getContractId, contractId)
                .eq(ContractAttachment::getDeleted, 0)
                .orderByDesc(ContractAttachment::getId));
    }

    private ContractVersionDto toVersionDto(ContractVersion version) {
        if (version == null) {
            return null;
        }
        return new ContractVersionDto(version.getId(), version.getVersionNo(), version.getTitle(), version.getContent(), version.getChangeNote(), version.getCreatedAt());
    }

    private ContractAttachmentDto toAttachmentDto(ContractAttachment attachment) {
        return new ContractAttachmentDto(attachment.getId(), attachment.getFileId(), attachment.getFileName(), attachment.getArtifactCode(), attachment.getCreatedAt());
    }

    private String nextContractNo(LocalDateTime now) {
        String prefix = "HT-" + now.getYear() + "-";
        Long count = contractInfoMapper.selectCount(new LambdaQueryWrapper<ContractInfo>()
                .likeRight(ContractInfo::getContractNo, prefix));
        return prefix + String.format(Locale.ROOT, "%04d", count + 1);
    }

    private BigDecimal normalizeAmount(BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) < 0) {
            throw new BusinessException("合同金额不能为负");
        }
        return amount;
    }

    private String statusName(String status) {
        return switch (status) {
            case "DRAFT" -> "草稿";
            case "SUBMITTED" -> "已提交";
            case "UNDER_REVIEW" -> "审核中";
            case "APPROVED" -> "已审批";
            case "REJECTED" -> "已驳回";
            case "ARCHIVED" -> "已归档";
            default -> status;
        };
    }

    private String detailJson(ContractInfo contract, String action) {
        return "{\"contractNo\":\"" + escape(contract.getContractNo()) + "\",\"status\":\""
                + escape(contract.getStatus()) + "\",\"action\":\"" + escape(action) + "\"}";
    }

    private String displayName(CurrentUserInfo user) {
        return hasText(user.realName()) ? user.realName() : user.username();
    }

    private String firstText(String value, String fallback) {
        return hasText(value) ? value : fallback;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private String escape(String value) {
        return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
