package com.example.judicialappraisal.knowledge.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.judicialappraisal.audit.service.AuditLogService;
import com.example.judicialappraisal.auth.dto.CurrentUserInfo;
import com.example.judicialappraisal.caseinfo.entity.CaseInfo;
import com.example.judicialappraisal.caseinfo.mapper.CaseInfoMapper;
import com.example.judicialappraisal.common.exception.BusinessException;
import com.example.judicialappraisal.file.dto.FileContent;
import com.example.judicialappraisal.file.service.FileStorageService;
import com.example.judicialappraisal.knowledge.dto.ArchiveNodeRequest;
import com.example.judicialappraisal.knowledge.dto.KnowledgeDirectoryDto;
import com.example.judicialappraisal.knowledge.dto.KnowledgeDocumentDto;
import com.example.judicialappraisal.knowledge.dto.KnowledgePermissionRequest;
import com.example.judicialappraisal.knowledge.entity.CaseArchiveRecord;
import com.example.judicialappraisal.knowledge.entity.KnowledgeDirectory;
import com.example.judicialappraisal.knowledge.entity.KnowledgeDocument;
import com.example.judicialappraisal.knowledge.entity.KnowledgeDocumentVersion;
import com.example.judicialappraisal.knowledge.entity.KnowledgePermission;
import com.example.judicialappraisal.knowledge.mapper.CaseArchiveRecordMapper;
import com.example.judicialappraisal.knowledge.mapper.KnowledgeDirectoryMapper;
import com.example.judicialappraisal.knowledge.mapper.KnowledgeDocumentMapper;
import com.example.judicialappraisal.knowledge.mapper.KnowledgeDocumentVersionMapper;
import com.example.judicialappraisal.knowledge.mapper.KnowledgePermissionMapper;
import com.example.judicialappraisal.workflow.entity.CaseTask;
import com.example.judicialappraisal.workflow.entity.CaseTaskCandidate;
import com.example.judicialappraisal.workflow.mapper.CaseTaskCandidateMapper;
import com.example.judicialappraisal.workflow.mapper.CaseTaskMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class KnowledgeService {

    private static final String PERMISSION_READ = "read";
    private static final String PERMISSION_DOWNLOAD = "download";
    private static final String SOURCE_ARCHIVE = "archive";

    private final KnowledgeDirectoryMapper directoryMapper;
    private final KnowledgeDocumentMapper documentMapper;
    private final KnowledgeDocumentVersionMapper versionMapper;
    private final KnowledgePermissionMapper permissionMapper;
    private final CaseArchiveRecordMapper archiveRecordMapper;
    private final CaseInfoMapper caseInfoMapper;
    private final CaseTaskMapper caseTaskMapper;
    private final CaseTaskCandidateMapper caseTaskCandidateMapper;
    private final FileStorageService fileStorageService;
    private final AuditLogService auditLogService;
    private final ObjectMapper objectMapper;

    public KnowledgeService(KnowledgeDirectoryMapper directoryMapper,
                            KnowledgeDocumentMapper documentMapper,
                            KnowledgeDocumentVersionMapper versionMapper,
                            KnowledgePermissionMapper permissionMapper,
                            CaseArchiveRecordMapper archiveRecordMapper,
                            CaseInfoMapper caseInfoMapper,
                            CaseTaskMapper caseTaskMapper,
                            CaseTaskCandidateMapper caseTaskCandidateMapper,
                            FileStorageService fileStorageService,
                            AuditLogService auditLogService,
                            ObjectMapper objectMapper) {
        this.directoryMapper = directoryMapper;
        this.documentMapper = documentMapper;
        this.versionMapper = versionMapper;
        this.permissionMapper = permissionMapper;
        this.archiveRecordMapper = archiveRecordMapper;
        this.caseInfoMapper = caseInfoMapper;
        this.caseTaskMapper = caseTaskMapper;
        this.caseTaskCandidateMapper = caseTaskCandidateMapper;
        this.fileStorageService = fileStorageService;
        this.auditLogService = auditLogService;
        this.objectMapper = objectMapper;
    }

    public List<KnowledgeDirectoryDto> directories(Long caseId) {
        ensureBaseDirectories();
        return directoryMapper.selectList(new LambdaQueryWrapper<KnowledgeDirectory>()
                        .eq(KnowledgeDirectory::getDeleted, 0)
                        .eq(caseId != null, KnowledgeDirectory::getCaseId, caseId)
                        .orderByAsc(KnowledgeDirectory::getSortNo)
                        .orderByAsc(KnowledgeDirectory::getId))
                .stream()
                .filter(directory -> canAccessDirectory(directory, PERMISSION_READ))
                .map(this::toDirectoryDto)
                .toList();
    }

    public List<KnowledgeDocumentDto> documents(Long directoryId, Long caseId, String keyword) {
        ensureBaseDirectories();
        return documentMapper.selectList(new LambdaQueryWrapper<KnowledgeDocument>()
                        .eq(KnowledgeDocument::getDeleted, 0)
                        .eq(directoryId != null, KnowledgeDocument::getDirectoryId, directoryId)
                        .eq(caseId != null, KnowledgeDocument::getCaseId, caseId)
                        .orderByDesc(KnowledgeDocument::getUpdatedTime)
                        .orderByDesc(KnowledgeDocument::getId))
                .stream()
                .filter(document -> canAccessDocument(document, PERMISSION_READ))
                .filter(document -> matchesKeyword(document, keyword))
                .map(this::toDocumentDto)
                .toList();
    }

    public FileContent downloadDocument(Long documentId) {
        KnowledgeDocument document = requireDocument(documentId);
        requireDocumentPermission(document, PERMISSION_DOWNLOAD);
        if (document.getCurrentFileId() == null) {
            throw new BusinessException("该归档文档暂无可下载文件");
        }
        auditLogService.record("KNOWLEDGE_DOWNLOAD", "知识文件下载", "knowledge_document", documentId, document.getCaseId(),
                "{\"title\":\"" + escape(document.getTitle()) + "\"}");
        return fileStorageService.download(document.getCurrentFileId());
    }

    public FileContent previewDocument(Long documentId) {
        KnowledgeDocument document = requireDocument(documentId);
        requireDocumentPermission(document, PERMISSION_READ);
        if (document.getCurrentFileId() == null) {
            throw new BusinessException("该归档文档暂无可预览文件");
        }
        auditLogService.record("KNOWLEDGE_PREVIEW", "知识文件预览", "knowledge_document", documentId, document.getCaseId(),
                "{\"title\":\"" + escape(document.getTitle()) + "\"}");
        return fileStorageService.preview(document.getCurrentFileId());
    }

    @Transactional
    public void grantPermission(KnowledgePermissionRequest request) {
        if ((request.directoryId() == null && request.documentId() == null)
                || request.subjectType() == null || request.subjectType().isBlank()
                || request.permissionCode() == null || request.permissionCode().isBlank()) {
            throw new BusinessException("权限配置不完整");
        }
        KnowledgePermission permission = new KnowledgePermission();
        permission.setDirectoryId(request.directoryId());
        permission.setDocumentId(request.documentId());
        permission.setSubjectType(request.subjectType());
        permission.setSubjectId(request.subjectId());
        permission.setPermissionCode(request.permissionCode());
        CurrentUserInfo user = currentUserOrNull();
        permission.setCreatedBy(user == null ? null : user.id());
        permission.setCreatedTime(LocalDateTime.now());
        permissionMapper.insert(permission);
        auditLogService.record("KNOWLEDGE_PERMISSION_GRANT", "知识权限授予",
                request.documentId() == null ? "knowledge_directory" : "knowledge_document",
                request.documentId() == null ? request.directoryId() : request.documentId(),
                null,
                toJson(Map.of("subjectType", request.subjectType(), "subjectId", request.subjectId(), "permission", request.permissionCode())));
    }

    @Transactional
    public KnowledgeDocumentDto archiveNode(ArchiveNodeRequest request) {
        if (request.caseId() == null) {
            throw new BusinessException("案件ID不能为空");
        }
        CaseInfo caseInfo = caseInfoMapper.selectById(request.caseId());
        if (caseInfo == null) {
            throw new BusinessException("案件不存在");
        }
        KnowledgeDirectory directory = ensureCaseDirectory(caseInfo);
        Long fileId = request.fileIds() == null || request.fileIds().isEmpty() ? null : request.fileIds().get(request.fileIds().size() - 1);
        String formSnapshot = toJson(request.formData() == null ? Map.of() : request.formData());
        if (fileId == null && request.formData() != null && !request.formData().isEmpty()) {
            String html = "<html><head><meta charset=\"utf-8\"></head><body style=\"font-family:sans-serif;padding:20px;\"><h2>" 
                    + request.nodeName() + " 办理记录</h2><pre style=\"background:#f5f5f5;padding:15px;border-radius:8px;\">" 
                    + formSnapshot + "</pre></body></html>";
            fileId = fileStorageService.uploadInternal("办理记录.html", "text/html; charset=utf-8", html.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        }

        Map<String, Object> archiveResultMap = new java.util.LinkedHashMap<>();
        archiveResultMap.put("nodeCode", valueOrEmpty(request.nodeCode()));
        archiveResultMap.put("nodeName", valueOrEmpty(request.nodeName()));
        archiveResultMap.put("taskId", request.taskId());
        archiveResultMap.put("fileIds", request.fileIds() == null ? List.of() : request.fileIds());
        archiveResultMap.put("summary", valueOrEmpty(request.archiveSummary()));
        String archiveResult = toJson(archiveResultMap);

        KnowledgeDocument document = null;
        if (request.artifactCode() != null && !request.artifactCode().isBlank()) {
            document = documentMapper.selectOne(new LambdaQueryWrapper<KnowledgeDocument>()
                    .eq(KnowledgeDocument::getCaseId, request.caseId())
                    .eq(KnowledgeDocument::getArtifactCode, request.artifactCode())
                    .eq(KnowledgeDocument::getDeleted, 0)
                    .last("limit 1"));
        } else if (request.nodeCode() != null && !request.nodeCode().isBlank()) {
            document = documentMapper.selectOne(new LambdaQueryWrapper<KnowledgeDocument>()
                    .eq(KnowledgeDocument::getCaseId, request.caseId())
                    .eq(KnowledgeDocument::getNodeCode, request.nodeCode())
                    .eq(KnowledgeDocument::getDeleted, 0)
                    .last("limit 1"));
        }

        int nextVersion = 1;
        CurrentUserInfo user = currentUserOrNull();
        if (document != null) {
            nextVersion = document.getCurrentVersionNo() + 1;
            document.setCurrentFileId(fileId);
            document.setCurrentVersionNo(nextVersion);
            document.setFormSnapshotJson(formSnapshot);
            document.setArchiveResultJson(archiveResult);
            document.setUpdatedBy(user == null ? null : user.id());
            document.setUpdatedTime(LocalDateTime.now());
            documentMapper.updateById(document);
        } else {
            document = new KnowledgeDocument();
            document.setDirectoryId(directory.getId());
            document.setCaseId(request.caseId());
            document.setTitle(request.title() == null || request.title().isBlank() ? defaultArchiveTitle(caseInfo, request) : request.title());
            document.setArtifactCode(request.artifactCode());
            document.setSourceType(SOURCE_ARCHIVE);
            document.setWfInstanceId(request.wfInstanceId());
            document.setNodeCode(request.nodeCode());
            document.setNodeName(request.nodeName());
            document.setTaskId(request.taskId());
            document.setCurrentFileId(fileId);
            document.setCurrentVersionNo(nextVersion);
            document.setFormSnapshotJson(formSnapshot);
            document.setArchiveResultJson(archiveResult);
            document.setStatus("active");
            document.setCreatedBy(user == null ? null : user.id());
            document.setUpdatedBy(user == null ? null : user.id());
            document.setCreatedTime(LocalDateTime.now());
            document.setUpdatedTime(LocalDateTime.now());
            document.setDeleted(0);
            documentMapper.insert(document);
        }

        KnowledgeDocumentVersion version = new KnowledgeDocumentVersion();
        version.setDocumentId(document.getId());
        version.setVersionNo(nextVersion);
        version.setFileId(fileId);
        version.setFormSnapshotJson(formSnapshot);
        version.setArchiveResultJson(archiveResult);
        version.setChangeNote(request.archiveSummary());
        version.setCreatedBy(user == null ? null : user.id());
        version.setCreatedTime(LocalDateTime.now());
        versionMapper.insert(version);

        CaseArchiveRecord record = new CaseArchiveRecord();
        record.setCaseId(caseInfo.getId());
        record.setCaseNo(caseInfo.getCaseNo());
        record.setWfInstanceId(request.wfInstanceId());
        record.setNodeCode(request.nodeCode());
        record.setNodeName(request.nodeName());
        record.setTaskId(request.taskId());
        record.setDocumentId(document.getId());
        record.setArchiveType("node");
        record.setArchiveStatus("archived");
        record.setArchiveSummary(request.archiveSummary());
        record.setArchivedBy(user == null ? null : user.id());
        record.setArchivedTime(LocalDateTime.now());
        archiveRecordMapper.insert(record);

        auditLogService.record("CASE_AUTO_ARCHIVE", "案件节点自动归档", "knowledge_document", document.getId(), caseInfo.getId(), archiveResult);
        return toDocumentDto(document);
    }

    @Transactional
    public KnowledgeDocumentDto archiveBusinessDocument(
            String bizType,
            Long bizId,
            String title,
            String artifactCode,
            Map<String, Object> formData,
            List<Long> fileIds,
            String archiveSummary) {
        if (bizId == null) {
            throw new BusinessException("业务对象ID不能为空");
        }
        ensureBaseDirectories();
        KnowledgeDirectory directory = ensureDirectory(
                null,
                "contract-archive",
                "合同档案",
                "public",
                null,
                null,
                null,
                "/合同档案",
                40);
        String effectiveArtifactCode = businessArtifactCode(bizType, bizId, artifactCode);
        Long fileId = fileIds == null || fileIds.isEmpty() ? null : fileIds.get(fileIds.size() - 1);
        Integer nextVersion = nextBusinessDocumentVersion(effectiveArtifactCode);
        String formSnapshot = toJson(formData == null ? Map.of() : formData);
        Map<String, Object> archiveResultMap = new java.util.LinkedHashMap<>();
        archiveResultMap.put("bizType", valueOrEmpty(bizType));
        archiveResultMap.put("bizId", bizId);
        archiveResultMap.put("fileIds", fileIds == null ? List.of() : fileIds);
        archiveResultMap.put("summary", valueOrEmpty(archiveSummary));
        String archiveResult = toJson(archiveResultMap);

        CurrentUserInfo user = currentUserOrNull();
        KnowledgeDocument document = new KnowledgeDocument();
        document.setDirectoryId(directory.getId());
        document.setTitle(title == null || title.isBlank() ? "业务归档-" + bizId : title);
        document.setArtifactCode(effectiveArtifactCode);
        document.setSourceType(SOURCE_ARCHIVE);
        document.setCurrentFileId(fileId);
        document.setCurrentVersionNo(nextVersion);
        document.setFormSnapshotJson(formSnapshot);
        document.setArchiveResultJson(archiveResult);
        document.setStatus("active");
        document.setCreatedBy(user == null ? null : user.id());
        document.setUpdatedBy(user == null ? null : user.id());
        document.setCreatedTime(LocalDateTime.now());
        document.setUpdatedTime(LocalDateTime.now());
        document.setDeleted(0);
        documentMapper.insert(document);

        KnowledgeDocumentVersion version = new KnowledgeDocumentVersion();
        version.setDocumentId(document.getId());
        version.setVersionNo(nextVersion);
        version.setFileId(fileId);
        version.setFormSnapshotJson(formSnapshot);
        version.setArchiveResultJson(archiveResult);
        version.setChangeNote(archiveSummary);
        version.setCreatedBy(user == null ? null : user.id());
        version.setCreatedTime(LocalDateTime.now());
        versionMapper.insert(version);

        auditLogService.record("BUSINESS_AUTO_ARCHIVE", "业务对象自动归档", "knowledge_document", document.getId(), null, archiveResult);
        return toDocumentDto(document);
    }

    @Transactional
    public KnowledgeDocumentDto uploadDocument(Long directoryId, String title, Long fileId) {
        if (directoryId == null) {
            throw new BusinessException("目录ID不能为空");
        }
        if (fileId == null) {
            throw new BusinessException("文件不能为空");
        }
        ensureBaseDirectories();
        CurrentUserInfo user = currentUserOrNull();

        KnowledgeDocument document = new KnowledgeDocument();
        document.setDirectoryId(directoryId);
        document.setTitle(title);
        document.setSourceType("knowledge");
        document.setCurrentFileId(fileId);
        document.setCurrentVersionNo(1);
        document.setStatus("active");
        document.setCreatedBy(user == null ? null : user.id());
        document.setUpdatedBy(user == null ? null : user.id());
        document.setCreatedTime(LocalDateTime.now());
        document.setUpdatedTime(LocalDateTime.now());
        document.setDeleted(0);
        documentMapper.insert(document);

        KnowledgeDocumentVersion version = new KnowledgeDocumentVersion();
        version.setDocumentId(document.getId());
        version.setVersionNo(1);
        version.setFileId(fileId);
        version.setChangeNote("初始手动上传");
        version.setCreatedBy(user == null ? null : user.id());
        version.setCreatedTime(LocalDateTime.now());
        versionMapper.insert(version);

        return toDocumentDto(document);
    }

    public void ensureBaseDirectories() {
        ensureDirectory(null, "public", "公共知识库", "public", null, null, null, "/公共知识库", 10);
        ensureDirectory(null, "department", "部门知识库", "dept", null, null, null, "/部门知识库", 20);
        ensureDirectory(null, "case-archive", "案件自动归档", "case", null, null, null, "/案件自动归档", 30);
    }

    private KnowledgeDirectory ensureCaseDirectory(CaseInfo caseInfo) {
        KnowledgeDirectory root = ensureDirectory(null, "case-archive", "案件自动归档", "case", null, null, null, "/案件自动归档", 30);
        String caseCode = "case-" + caseInfo.getId();
        String name = caseInfo.getCaseNo() == null || caseInfo.getCaseNo().isBlank() ? "案件-" + caseInfo.getId() : caseInfo.getCaseNo();
        return ensureDirectory(root.getId(), caseCode, name, "case", caseInfo.getId(), caseInfo.getAcceptDeptId(), null,
                root.getPath() + "/" + name, 0);
    }

    private String businessArtifactCode(String bizType, Long bizId, String artifactCode) {
        return artifactCode == null || artifactCode.isBlank() ? valueOrEmpty(bizType) + "-" + bizId : artifactCode;
    }

    private Integer nextBusinessDocumentVersion(String artifactCode) {
        return documentMapper.selectList(new LambdaQueryWrapper<KnowledgeDocument>()
                        .eq(KnowledgeDocument::getSourceType, SOURCE_ARCHIVE)
                        .eq(KnowledgeDocument::getArtifactCode, artifactCode)
                        .eq(KnowledgeDocument::getDeleted, 0)
                        .orderByDesc(KnowledgeDocument::getCurrentVersionNo)
                        .last("limit 1"))
                .stream()
                .findFirst()
                .map(KnowledgeDocument::getCurrentVersionNo)
                .orElse(0) + 1;
    }

    private boolean matchesKeyword(KnowledgeDocument document, String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return true;
        }
        String normalizedKeyword = keyword.trim().toLowerCase(java.util.Locale.ROOT);
        if (containsIgnoreCase(document.getTitle(), normalizedKeyword)
                || containsIgnoreCase(document.getArtifactCode(), normalizedKeyword)
                || containsIgnoreCase(document.getNodeName(), normalizedKeyword)
                || containsIgnoreCase(document.getFormSnapshotJson(), normalizedKeyword)
                || containsIgnoreCase(document.getArchiveResultJson(), normalizedKeyword)) {
            return true;
        }
        if (document.getCurrentFileId() == null) {
            return false;
        }
        return containsIgnoreCase(fileStorageService.extractText(document.getCurrentFileId()), normalizedKeyword);
    }

    private boolean containsIgnoreCase(String source, String normalizedKeyword) {
        if (source == null || source.isBlank()) {
            return false;
        }
        return source.toLowerCase(java.util.Locale.ROOT).contains(normalizedKeyword);
    }

    private KnowledgeDirectory ensureDirectory(Long parentId, String code, String name, String type, Long caseId,
                                               Long deptId, Long ownerUserId, String path, int sortNo) {
        KnowledgeDirectory existing = directoryMapper.selectOne(new LambdaQueryWrapper<KnowledgeDirectory>()
                .eq(KnowledgeDirectory::getDirectoryCode, code)
                .eq(KnowledgeDirectory::getDeleted, 0)
                .last("limit 1"));
        if (existing != null) {
            return existing;
        }
        KnowledgeDirectory directory = new KnowledgeDirectory();
        directory.setParentId(parentId);
        directory.setDirectoryCode(code);
        directory.setDirectoryName(name);
        directory.setDirectoryType(type);
        directory.setCaseId(caseId);
        directory.setDeptId(deptId);
        directory.setOwnerUserId(ownerUserId);
        directory.setPath(path);
        directory.setSortNo(sortNo);
        CurrentUserInfo user = currentUserOrNull();
        directory.setCreatedBy(user == null ? null : user.id());
        directory.setUpdatedBy(user == null ? null : user.id());
        directory.setCreatedTime(LocalDateTime.now());
        directory.setUpdatedTime(LocalDateTime.now());
        directory.setDeleted(0);
        directoryMapper.insert(directory);
        grantAllReadDownload(directory.getId());
        return directory;
    }

    private void grantAllReadDownload(Long directoryId) {
        for (String permissionCode : List.of(PERMISSION_READ, PERMISSION_DOWNLOAD)) {
            KnowledgePermission permission = new KnowledgePermission();
            permission.setDirectoryId(directoryId);
            permission.setSubjectType("all");
            permission.setPermissionCode(permissionCode);
            permission.setCreatedTime(LocalDateTime.now());
            permissionMapper.insert(permission);
        }
    }

    private Integer nextDocumentVersion(ArchiveNodeRequest request) {
        KnowledgeDocument latest = documentMapper.selectOne(new LambdaQueryWrapper<KnowledgeDocument>()
                .eq(KnowledgeDocument::getCaseId, request.caseId())
                .eq(request.artifactCode() != null && !request.artifactCode().isBlank(), KnowledgeDocument::getArtifactCode, request.artifactCode())
                .eq(request.taskId() != null, KnowledgeDocument::getTaskId, request.taskId())
                .eq(KnowledgeDocument::getDeleted, 0)
                .orderByDesc(KnowledgeDocument::getCurrentVersionNo)
                .last("limit 1"));
        return latest == null ? 1 : latest.getCurrentVersionNo() + 1;
    }

    private boolean canAccessDocument(KnowledgeDocument document, String permissionCode) {
        if (document == null) {
            return false;
        }
        if (document.getCaseId() != null && !canAccessArchivedCase(document.getCaseId())) {
            return false;
        }
        if (hasPermission(null, document.getId(), permissionCode)) {
            return true;
        }
        KnowledgeDirectory directory = directoryMapper.selectById(document.getDirectoryId());
        return canAccessDirectory(directory, permissionCode);
    }

    private boolean canAccessDirectory(KnowledgeDirectory directory, String permissionCode) {
        if (directory == null) {
            return false;
        }
        if (directory.getCaseId() != null && !canAccessArchivedCase(directory.getCaseId())) {
            return false;
        }
        CurrentUserInfo user = currentUserOrNull();
        if ("personal".equals(directory.getDirectoryType()) && user != null && user.id().equals(directory.getOwnerUserId())) {
            return true;
        }
        if ("dept".equals(directory.getDirectoryType()) && user != null && user.deptId() != null && user.deptId().equals(directory.getDeptId())) {
            return true;
        }
        return hasPermission(directory.getId(), null, permissionCode);
    }

    private boolean canAccessArchivedCase(Long caseId) {
        CurrentUserInfo user = currentUserOrNull();
        if (user == null) {
            return false;
        }
        CaseInfo caseInfo = caseInfoMapper.selectById(caseId);
        if (caseInfo == null || Objects.equals(caseInfo.getDeleted(), 1)) {
            return false;
        }
        if (user.roles().stream().anyMatch(role -> "ADMIN".equalsIgnoreCase(role.code()))) {
            return true;
        }
        Long userId = user.id();
        if (Objects.equals(caseInfo.getCreatedBy(), userId) || Objects.equals(caseInfo.getCurrentHandlerId(), userId)) {
            return true;
        }
        List<CaseTask> tasks = caseTaskMapper.selectList(new LambdaQueryWrapper<CaseTask>()
                .eq(CaseTask::getCaseId, caseId)
                .and(wrapper -> wrapper
                        .eq(CaseTask::getAssigneeId, userId)
                        .or()
                        .eq(CaseTask::getClaimedBy, userId)));
        if (!tasks.isEmpty()) {
            return true;
        }
        List<Long> activeTaskIds = caseTaskMapper.selectList(new LambdaQueryWrapper<CaseTask>()
                        .select(CaseTask::getId)
                        .eq(CaseTask::getCaseId, caseId)
                        .in(CaseTask::getStatus, "pending", "processing"))
                .stream()
                .map(CaseTask::getId)
                .toList();
        if (activeTaskIds.isEmpty()) {
            return false;
        }
        List<Long> roleIds = user.roles().stream()
                .map(com.example.judicialappraisal.auth.dto.CurrentUserRole::id)
                .filter(Objects::nonNull)
                .toList();
        return caseTaskCandidateMapper.selectCount(new LambdaQueryWrapper<CaseTaskCandidate>()
                .eq(CaseTaskCandidate::getCaseId, caseId)
                .in(CaseTaskCandidate::getTaskId, activeTaskIds)
                .and(wrapper -> {
                    wrapper.eq(CaseTaskCandidate::getCandidateUserId, userId);
                    if (!roleIds.isEmpty()) {
                        wrapper.or().in(CaseTaskCandidate::getCandidateRoleId, roleIds);
                    }
                })) > 0;
    }

    private boolean hasPermission(Long directoryId, Long documentId, String permissionCode) {
        List<KnowledgePermission> permissions = permissionMapper.selectList(new LambdaQueryWrapper<KnowledgePermission>()
                .eq(directoryId != null, KnowledgePermission::getDirectoryId, directoryId)
                .eq(documentId != null, KnowledgePermission::getDocumentId, documentId)
                .eq(KnowledgePermission::getPermissionCode, permissionCode));
        if (permissions == null || permissions.isEmpty()) {
            return false;
        }
        CurrentUserInfo user = currentUserOrNull();
        Set<Long> roleIds = user == null ? Set.of() : user.roles().stream()
                .map(com.example.judicialappraisal.auth.dto.CurrentUserRole::id)
                .filter(java.util.Objects::nonNull)
                .collect(java.util.stream.Collectors.toSet());
        for (KnowledgePermission permission : permissions) {
            if ("all".equalsIgnoreCase(permission.getSubjectType())) {
                return true;
            }
            if (user == null) {
                continue;
            }
            if ("user".equalsIgnoreCase(permission.getSubjectType()) && user.id().equals(permission.getSubjectId())) {
                return true;
            }
            if ("dept".equalsIgnoreCase(permission.getSubjectType()) && user.deptId() != null && user.deptId().equals(permission.getSubjectId())) {
                return true;
            }
            if ("role".equalsIgnoreCase(permission.getSubjectType()) && permission.getSubjectId() != null
                    && roleIds.contains(permission.getSubjectId())) {
                return true;
            }
        }
        return false;
    }

    private KnowledgeDocument requireDocument(Long documentId) {
        KnowledgeDocument document = documentMapper.selectById(documentId);
        if (document == null || Integer.valueOf(1).equals(document.getDeleted())) {
            throw new BusinessException("知识文档不存在");
        }
        return document;
    }

    private void requireDocumentPermission(KnowledgeDocument document, String permissionCode) {
        if (!canAccessDocument(document, permissionCode)) {
            throw new BusinessException(403, "无权访问该知识文档");
        }
    }

    private KnowledgeDirectoryDto toDirectoryDto(KnowledgeDirectory directory) {
        return new KnowledgeDirectoryDto(
                directory.getId(),
                directory.getParentId(),
                directory.getDirectoryCode(),
                directory.getDirectoryName(),
                directory.getDirectoryType(),
                directory.getCaseId(),
                directory.getPath()
        );
    }

    private KnowledgeDocumentDto toDocumentDto(KnowledgeDocument document) {
        return new KnowledgeDocumentDto(
                document.getId(),
                document.getDirectoryId(),
                document.getCaseId(),
                document.getTitle(),
                document.getArtifactCode(),
                document.getSourceType(),
                document.getNodeCode(),
                document.getNodeName(),
                document.getTaskId(),
                document.getCurrentFileId(),
                document.getCurrentVersionNo(),
                document.getStatus(),
                document.getUpdatedTime()
        );
    }

    private String defaultArchiveTitle(CaseInfo caseInfo, ArchiveNodeRequest request) {
        String caseNo = caseInfo.getCaseNo() == null ? "案件-" + caseInfo.getId() : caseInfo.getCaseNo();
        return caseNo + " / " + valueOrEmpty(request.nodeName()) + " / 任务" + request.taskId();
    }

    private CurrentUserInfo currentUserOrNull() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof CurrentUserInfo userInfo)) {
            return null;
        }
        return userInfo;
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            return "{}";
        }
    }

    private String valueOrEmpty(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private String escape(String value) {
        return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
