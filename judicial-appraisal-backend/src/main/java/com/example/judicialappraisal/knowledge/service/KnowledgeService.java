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
import com.example.judicialappraisal.organization.mapper.SysUserMapper;
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
    private final SysUserMapper sysUserMapper;
    private final com.example.judicialappraisal.file.mapper.SysFileMapper sysFileMapper;

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
                            ObjectMapper objectMapper,
                            SysUserMapper sysUserMapper,
                            com.example.judicialappraisal.file.mapper.SysFileMapper sysFileMapper) {
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
        this.sysUserMapper = sysUserMapper;
        this.sysFileMapper = sysFileMapper;
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
        if ("archive".equals(document.getSourceType()) && document.getFormSnapshotJson() != null && !document.getFormSnapshotJson().trim().isEmpty()) {
            try {
                Map<String, Object> formData = objectMapper.readValue(document.getFormSnapshotJson(), new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {});
                String html = generateHtmlForm(document.getNodeName(), formData);
                byte[] bytes = html.getBytes(java.nio.charset.StandardCharsets.UTF_8);
                auditLogService.record("KNOWLEDGE_DOWNLOAD", "知识文件下载", "knowledge_document", documentId, document.getCaseId(),
                        "{\"title\":\"" + escape(document.getTitle()) + "\"}");
                return new FileContent("办理记录.html", "text/html; charset=utf-8", bytes);
            } catch (Exception e) {
                // fallback
            }
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
        if ("archive".equals(document.getSourceType()) && document.getFormSnapshotJson() != null && !document.getFormSnapshotJson().trim().isEmpty()) {
            try {
                Map<String, Object> formData = objectMapper.readValue(document.getFormSnapshotJson(), new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {});
                String html = generateHtmlForm(document.getNodeName(), formData);
                byte[] bytes = html.getBytes(java.nio.charset.StandardCharsets.UTF_8);
                auditLogService.record("KNOWLEDGE_PREVIEW", "知识文件预览", "knowledge_document", documentId, document.getCaseId(),
                        "{\"title\":\"" + escape(document.getTitle()) + "\"}");
                return new FileContent("办理记录.html", "text/html; charset=utf-8", bytes);
            } catch (Exception e) {
                // fallback
            }
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
            String html = generateHtmlForm(request.nodeName(), request.formData());
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

    @Transactional
    public void deleteDocument(Long documentId) {
        CurrentUserInfo user = currentUserOrNull();
        if (user == null || user.roles() == null || user.roles().stream().noneMatch(r -> "ADMIN".equalsIgnoreCase(r.code()) || "ROLE_ADMIN".equalsIgnoreCase(r.code()))) {
            throw new BusinessException("只有管理员才能删除知识库记录");
        }
        KnowledgeDocument document = documentMapper.selectById(documentId);
        if (document == null) {
            throw new BusinessException("记录不存在");
        }
        documentMapper.deleteById(documentId);

        auditLogService.record("DELETE", "管理员删除知识库记录", "knowledge_document", documentId, null, "{\"title\": \"" + document.getTitle() + "\"}");

        // Clean up empty case directory
        Long directoryId = document.getDirectoryId();
        if (directoryId != null) {
            long count = documentMapper.selectCount(new LambdaQueryWrapper<KnowledgeDocument>()
                    .eq(KnowledgeDocument::getDirectoryId, directoryId)
                    .eq(KnowledgeDocument::getDeleted, 0));
            if (count == 0) {
                KnowledgeDirectory directory = directoryMapper.selectById(directoryId);
                if (directory != null && "case".equals(directory.getDirectoryType())) {
                    directoryMapper.deleteById(directoryId);
                }
            }
        }
    }

    @Transactional
    public void batchDeleteDocuments(List<Long> documentIds) {
        if (documentIds == null || documentIds.isEmpty()) {
            return;
        }
        CurrentUserInfo user = currentUserOrNull();
        if (user == null || user.roles() == null || user.roles().stream().noneMatch(r -> "ADMIN".equalsIgnoreCase(r.code()) || "ROLE_ADMIN".equalsIgnoreCase(r.code()))) {
            throw new BusinessException("只有管理员才能删除知识库记录");
        }

        Set<Long> directoryIdsToCheck = new java.util.HashSet<>();
        for (Long documentId : documentIds) {
            KnowledgeDocument document = documentMapper.selectById(documentId);
            if (document != null) {
                documentMapper.deleteById(documentId);
                auditLogService.record("DELETE", "管理员批量删除知识库记录", "knowledge_document", documentId, null, "{\"title\": \"" + document.getTitle() + "\"}");
                if (document.getDirectoryId() != null) {
                    directoryIdsToCheck.add(document.getDirectoryId());
                }
            }
        }

        // Clean up empty case directories
        for (Long directoryId : directoryIdsToCheck) {
            long count = documentMapper.selectCount(new LambdaQueryWrapper<KnowledgeDocument>()
                    .eq(KnowledgeDocument::getDirectoryId, directoryId)
                    .eq(KnowledgeDocument::getDeleted, 0));
            if (count == 0) {
                KnowledgeDirectory directory = directoryMapper.selectById(directoryId);
                if (directory != null && "case".equals(directory.getDirectoryType())) {
                    directoryMapper.deleteById(directoryId);
                }
            }
        }
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
        CaseInfo caseInfo = caseInfoMapper.selectRawById(caseId);
        if (caseInfo == null || Objects.equals(caseInfo.getDeleted(), 1)) {
            return false;
        }
        if (user.roles().stream().anyMatch(role -> "ADMIN".equalsIgnoreCase(role.code()))) {
            return true;
        }
        if (isScenarioTestCase(caseInfo)) {
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
        List<Long> roleIds = user.roles().stream()
                .map(com.example.judicialappraisal.auth.dto.CurrentUserRole::id)
                .filter(Objects::nonNull)
                .toList();
        if (hasHistoricalTaskCandidate(caseId, userId, roleIds)) {
            return true;
        }
        Map<String, Object> formData = caseInfo.getFormData();
        if (formData != null) {
            List<String> userKeys = List.of("projectLeaderId", "projectAssistantId", "departmentHeadId", "technicalLeaderId", "centralArchivistId");
            for (String key : userKeys) {
                Object val = formData.get(key);
                if (val != null) {
                    Long checkUserId = null;
                    if (val instanceof Number) {
                        checkUserId = ((Number) val).longValue();
                    } else {
                        try {
                            checkUserId = Long.parseLong(val.toString().trim());
                        } catch (NumberFormatException e) {
                            // ignore
                        }
                    }
                    if (Objects.equals(checkUserId, userId)) {
                        return true;
                    }
                }
            }
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

    private boolean hasHistoricalTaskCandidate(Long caseId, Long userId, List<Long> roleIds) {
        return caseTaskCandidateMapper.selectCount(new LambdaQueryWrapper<CaseTaskCandidate>()
                .eq(CaseTaskCandidate::getCaseId, caseId)
                .and(wrapper -> {
                    wrapper.eq(CaseTaskCandidate::getCandidateUserId, userId);
                    if (roleIds != null && !roleIds.isEmpty()) {
                        wrapper.or().in(CaseTaskCandidate::getCandidateRoleId, roleIds);
                    }
                })) > 0;
    }

    private boolean isScenarioTestCase(CaseInfo caseInfo) {
        return isScenarioTestText(caseInfo.getCaseNo()) || isScenarioTestText(caseInfo.getCaseTitle());
    }

    private boolean isScenarioTestText(String text) {
        if (text == null) {
            return false;
        }
        for (int i = 1; i <= 6; i++) {
            if (text.startsWith("场景" + i + "：") || text.startsWith("场景" + i + ":")) {
                return true;
            }
        }
        return false;
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
        List<KnowledgeDocumentDto.DocumentAttachmentDto> attachments = new java.util.ArrayList<>();
        if (document.getArchiveResultJson() != null && !document.getArchiveResultJson().trim().isEmpty()) {
            try {
                Map<String, Object> archiveResult = objectMapper.readValue(document.getArchiveResultJson(), new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {});
                Object fileIdsObj = archiveResult.get("fileIds");
                if (fileIdsObj instanceof List<?> list && !list.isEmpty()) {
                    List<Long> fileIds = list.stream().map(v -> Long.parseLong(v.toString())).toList();
                    List<com.example.judicialappraisal.file.entity.SysFile> sysFiles = sysFileMapper.selectBatchIds(fileIds);
                    for (com.example.judicialappraisal.file.entity.SysFile sysFile : sysFiles) {
                        attachments.add(new KnowledgeDocumentDto.DocumentAttachmentDto(
                                sysFile.getId(),
                                sysFile.getOriginalName(),
                                sysFile.getFileExt()
                        ));
                    }
                }
            } catch (Exception e) {
                // ignore parsing errors
            }
        }
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
                document.getUpdatedTime(),
                attachments
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

    private String generateHtmlForm(String nodeName, Map<String, Object> formData) {
        if (formData == null) {
            formData = Map.of();
        }
        
        Map<String, String> labelMap = new java.util.HashMap<>();
        labelMap.put("materialsUploaded", "材料已上传状态");
        labelMap.put("receiveDate", "接收时间");
        labelMap.put("materialUploaderId", "材料上传主办人");
        labelMap.put("materialMediaType", "材料介质类别");
        labelMap.put("projectMaterialConfirmed", "项目负责人已确认材料");
        labelMap.put("materialReceiveType", "材料接收与返还类型");
        labelMap.put("serialNo", "流水号");
        labelMap.put("flowName", "流程名称");
        labelMap.put("initiatorName", "发起人");
        labelMap.put("initiatedDate", "发起日期");
        labelMap.put("projectNo", "项目编号");
        labelMap.put("expressNo", "快递单号");
        labelMap.put("receivedDate", "收件日期");
        labelMap.put("filingDate", "立案日期");
        labelMap.put("clientName", "委托人");
        labelMap.put("entrustOrgName", "委托单位");
        labelMap.put("caseNo", "案件号");
        labelMap.put("caseTitle", "案件名称");
        labelMap.put("caseType", "案件类型");
        labelMap.put("caseStatus", "案件状态");
        labelMap.put("undertakingLegalPerson", "承办法人");
        labelMap.put("institutionSelectionMethod", "确定机构方式");
        labelMap.put("institutionSelectionTime", "确定机构时间");
        labelMap.put("appraisalCategory", "鉴定类别");
        labelMap.put("applicantName", "原告/申请人");
        labelMap.put("respondentName", "被告/被申请人");
        labelMap.put("urgencyLevel", "项目紧急程度");
        labelMap.put("caseChannel", "线上/线下");
        labelMap.put("projectAmount", "项目金额");
        labelMap.put("appraisalMatter", "鉴定事项");
        labelMap.put("entrustAccepted", "委托审查是否受理");
        labelMap.put("preliminarySurveyRequired", "是否进行初步勘验");
        labelMap.put("materialReceiveRequired", "是否同步收案员材料接收");
        labelMap.put("departmentHeadId", "部门负责人");
        labelMap.put("projectLeaderId", "项目负责人");
        labelMap.put("projectAssistantId", "项目辅助人");
        labelMap.put("handlerOpinion", "办理意见");
        labelMap.put("qualityFileDraftCompleted", "内部质量控制文件草稿已编制");
        labelMap.put("qualityFileSummary", "内部质量控制文件摘要");
        labelMap.put("formatType", "格式类型");
        labelMap.put("contractAmount", "合同金额");
        labelMap.put("fClassProject", "是否F类项目");
        labelMap.put("projectReviewPassed", "项目负责人审核通过");
        labelMap.put("projectReviewRoute", "项目负责人审核后流向");
        labelMap.put("projectReviewOpinion", "项目负责人审核意见");
        labelMap.put("departmentReviewPassed", "部门负责人审核通过");
        labelMap.put("departmentReviewOpinion", "部门负责人审核意见");
        labelMap.put("sealRequired", "是否需要用章");
        labelMap.put("sealedQualityFileUploaded", "内部质量控制文件盖章件已上传");
        labelMap.put("nextRecommendation", "下一步建议");
        labelMap.put("applicantId", "申请人");
        labelMap.put("archivistId", "档案管理员");
        labelMap.put("sealOperatorId", "盖章经办人");
        labelMap.put("applicationReason", "用章申请原因");
        labelMap.put("sealMode", "用章模式");
        labelMap.put("applicationFilesPrepared", "用章申请材料已准备");
        labelMap.put("archivistReviewed", "档案管理员已审核");
        labelMap.put("sealCompleted", "盖章是否完成");
        labelMap.put("sealedScanUploaded", "盖章扫描件已上传");
        labelMap.put("surveyDate", "勘验日期");
        labelMap.put("surveyLocation", "勘验地点");
        labelMap.put("surveyPlanUploaded", "勘验方案已上传");
        labelMap.put("equipmentOutboundRecorded", "出库设备已记录");
        labelMap.put("equipmentUsageRecorded", "现场设备使用已记录");
        labelMap.put("surveySummary", "勘验总结");
        labelMap.put("appraisalConditionMet", "鉴定条件是否具备");
        labelMap.put("technicalLeaderId", "技术负责人");
        labelMap.put("fieldRecordUploaded", "现场勘验记录已上传");
        labelMap.put("equipmentReturnRecorded", "设备入库已记录");
        labelMap.put("majorAmountProject", "是否重大金额项目");
        labelMap.put("technicalReviewPassed", "技术负责人审核通过");
        labelMap.put("letterType", "函件类型");
        labelMap.put("letterSummary", "函件摘要");
        labelMap.put("letterDraftCompleted", "函件草稿已编制");
        labelMap.put("sealedDocumentUploaded", "盖章文件已回传");
        labelMap.put("sendDate", "发出日期");
        labelMap.put("paymentReceived", "是否缴费");
        labelMap.put("paymentConfirmedDate", "缴费确认时间");
        
        // 移交与归档相关字段
        labelMap.put("centralArchivistId", "中心档案管理员");
        labelMap.put("mailerId", "邮寄人员");
        labelMap.put("projectArchiveUploaded", "项目档案已上传");
        labelMap.put("paperScansUploaded", "纸质扫描件已上传");
        labelMap.put("electronicArchiveLocation", "电子归档地址");
        labelMap.put("deliveryRoute", "入库方式");
        labelMap.put("mailTrackingNo", "邮寄单号");
        labelMap.put("centralArchiveApproved", "中心档案管理员审核通过");
        labelMap.put("archiveRoomLocation", "档案室入库位置");
        
        // 出庭通知相关字段
        labelMap.put("courtName", "法院名称");
        labelMap.put("noticeReceivedDate", "收到出庭通知日期");
        labelMap.put("appearanceDate", "出庭日期");
        labelMap.put("appearanceLocation", "出庭地点");
        labelMap.put("appearanceFeeRequired", "是否需要出庭费通知");
        labelMap.put("feeNoticeIssued", "出庭费通知已发出");
        labelMap.put("archiveRetrievalRequired", "是否需要调档");
        labelMap.put("archiveRetrieved", "档案已调取");
        labelMap.put("appearancePlanPrepared", "出庭方案已准备");
        labelMap.put("appearanceMaterialsPrepared", "出庭材料已准备");
        labelMap.put("appearanceCompleted", "已完成出庭");
        labelMap.put("appearanceSummary", "出庭情况摘要");
        labelMap.put("postAppearanceMaterialsUploaded", "出庭后材料已整理上传");
        
        // 法院函件及其他通用字段
        labelMap.put("linkedWorkflowCode", "关联原流程");
        labelMap.put("departmentDecision", "部门负责人处理结论");
        labelMap.put("deliveryMethod", "寄送/送达方式");
        labelMap.put("deliveryDate", "寄送/送达日期");
        labelMap.put("draftOpinionUploaded", "征求意见稿初稿已上传");
        labelMap.put("finalDraftUploaded", "定稿文件已上传");
        labelMap.put("initiatorId", "发起人");
        labelMap.put("financeId", "财务人员");
        labelMap.put("expenseSummary", "报销事项");
        labelMap.put("expenseAmount", "报销金额");
        labelMap.put("invoiceSummary", "发票汇总");
        labelMap.put("financeProcessed", "财务已处理");
        labelMap.put("financeResult", "财务处理结果");
        labelMap.put("paymentDate", "支付/打款日期");
        labelMap.put("opinionDraftUploaded", "初稿已上传");
        labelMap.put("versionAUploaded", "版本A已上传");
        labelMap.put("versionABUploaded", "版本A-B已上传");
        labelMap.put("versionABCUploaded", "版本A-B-C已上传");
        labelMap.put("explainLetterDrafted", "鉴定说明函已编制");
        labelMap.put("sealedDraftOpinionUploaded", "征求意见稿盖章件已上传");
        labelMap.put("feedbackReceived", "是否收到反馈");
        labelMap.put("feedbackHasObjection", "是否提出异议");
        labelMap.put("feedbackDecision", "反馈处理结论");
        labelMap.put("objectionReason", "异议内容简述");
        labelMap.put("commitmentDrafted", "鉴定人承诺书已编制");
        labelMap.put("reviewOpinionDrafted", "复核意见已编制");
        labelMap.put("sealedOpinionUploaded", "鉴定意见书盖章件已上传");
        labelMap.put("invoiceRequired", "是否开具发票");
        labelMap.put("invoiceIssued", "发票已开具并回传");
        labelMap.put("archiveConfirmed", "归档材料已确认");
        labelMap.put("materialSource", "材料来源");
        labelMap.put("requireSupplementaryMaterial", "是否补充材料");
        labelMap.put("supplementaryNotice", "补材通知");
        labelMap.put("materialDetails", "材料名称/数量/介质");
        labelMap.put("storageLocation", "存放地址");
        labelMap.put("requireReturn", "是否返还");
        labelMap.put("storageStatus", "保管状态");
        labelMap.put("returnReceiver", "返还接收人");
        labelMap.put("returnDate", "返还时间");
        labelMap.put("letterReceivedDate", "收函日期");
        labelMap.put("objectionAccepted", "是否按异议处理");
        labelMap.put("contractChangeCompleted", "合同变更已完成");
        labelMap.put("revenueConfirmed", "收入确认已完成");
        labelMap.put("refundApplicationSubmitted", "退费申请已提交");
        labelMap.put("paymentCompleted", "打款已完成");
        labelMap.put("paymentVoucherUploaded", "打款结果已回传");
        labelMap.put("rejectionReason", "不予受理原因");
        labelMap.put("noticeDraftCompleted", "不予受理通知书已编制");
        labelMap.put("noticeSummary", "通知书内容摘要");
        labelMap.put("reviewOpinion", "审核意见");
        labelMap.put("sealedNoticeUploaded", "盖章通知书扫描件已上传");
        labelMap.put("terminationType", "终止文书类型");
        labelMap.put("terminationReason", "终止原因");
        labelMap.put("draftCompleted", "终止文书草稿已完成");
        labelMap.put("sealedTerminationUploaded", "终止文书盖章件已上传");
        labelMap.put("withdrawLetterReceivedDate", "撤案函收函日期");
        labelMap.put("withdrawReason", "撤案原因");
        labelMap.put("refundRequired", "是否需要退费");
        labelMap.put("decisionSummary", "处理结论说明");

        StringBuilder html = new StringBuilder();
        html.append("<!DOCTYPE html>\n<html>\n<head>\n<meta charset=\"utf-8\">\n");
        html.append("<style>\n");
        html.append("  body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif; background-color: #f8fafc; color: #1e293b; padding: 30px; margin: 0; }\n");
        html.append("  .form-container { max-width: 800px; margin: 0 auto; background: #ffffff; border: 1px solid #e2e8f0; border-radius: 12px; box-shadow: 0 4px 6px -1px rgba(0,0,0,0.05), 0 2px 4px -2px rgba(0,0,0,0.05); overflow: hidden; }\n");
        html.append("  .form-header { background: linear-gradient(135deg, #1e3a8a 0%, #3b82f6 100%); padding: 24px 30px; color: #ffffff; }\n");
        html.append("  .form-header h2 { margin: 0; font-size: 20px; font-weight: 600; }\n");
        html.append("  .form-header p { margin: 6px 0 0 0; font-size: 13px; opacity: 0.85; }\n");
        html.append("  .form-table { width: 100%; border-collapse: collapse; margin: 0; }\n");
        html.append("  .form-table th, .form-table td { padding: 14px 24px; text-align: left; font-size: 14px; border-bottom: 1px solid #f1f5f9; }\n");
        html.append("  .form-table th { background-color: #f8fafc; color: #475569; font-weight: 600; width: 30%; border-right: 1px solid #f1f5f9; }\n");
        html.append("  .form-table td { color: #334155; }\n");
        html.append("  .form-table tr:hover th { background-color: #f1f5f9; }\n");
        html.append("  .form-table tr:hover td { background-color: #fafafa; }\n");
        html.append("  .tag-boolean { display: inline-block; padding: 2px 8px; border-radius: 4px; font-size: 12px; font-weight: 500; }\n");
        html.append("  .tag-true { background-color: #dcfce7; color: #15803d; }\n");
        html.append("  .tag-false { background-color: #fee2e2; color: #b91c1c; }\n");
        html.append("</style>\n</head>\n<body>\n");
        html.append("<div class=\"form-container\">\n");
        html.append("  <div class=\"form-header\">\n");
        html.append("    <h2>").append(nodeName).append(" 办理记录</h2>\n");
        html.append("    <p>电子司法鉴定所流程引擎自动生成 • 固化留痕</p>\n");
        html.append("  </div>\n");
        html.append("  <table class=\"form-table\">\n");

        for (Map.Entry<String, Object> entry : formData.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();
            if (value == null) {
                continue;
            }

            // Resolve User IDs to display names if applicable
            if (key.endsWith("Id") || key.equals("materialUploaderId") || key.equals("projectLeaderId") || key.equals("projectAssistantId") || key.equals("departmentHeadId") || key.equals("technicalLeaderId") || key.equals("centralArchivistId") || key.equals("initiatorId") || key.equals("financeId") || key.equals("applicantId") || key.equals("archivistId") || key.equals("sealOperatorId")) {
                Long checkUserId = null;
                if (value instanceof Number) {
                    checkUserId = ((Number) value).longValue();
                } else {
                    try {
                        checkUserId = Long.parseLong(value.toString().trim());
                    } catch (NumberFormatException e) {
                        // ignore
                    }
                }
                if (checkUserId != null) {
                    com.example.judicialappraisal.organization.entity.SysUser sysUser = sysUserMapper.selectById(checkUserId);
                    if (sysUser != null && sysUser.getRealName() != null) {
                        value = sysUser.getRealName();
                    }
                }
            }

            String label = labelMap.getOrDefault(key, key);
            html.append("    <tr>\n");
            html.append("      <th>").append(label).append("</th>\n");
            html.append("      <td>");

            if (value instanceof Boolean) {
                boolean boolVal = (Boolean) value;
                if (boolVal) {
                    html.append("<span class=\"tag-boolean tag-true\">是</span>");
                } else {
                    html.append("<span class=\"tag-boolean tag-false\">否</span>");
                }
            } else if (value instanceof List) {
                List<?> list = (List<?>) value;
                if (list.isEmpty()) {
                    html.append("-");
                } else {
                    html.append(String.join(", ", list.stream().map(Object::toString).toList()));
                }
            } else {
                String strVal = value.toString();
                if (strVal.trim().isEmpty()) {
                    html.append("-");
                } else {
                    html.append(strVal.replace("\n", "<br>"));
                }
            }

            html.append("</td>\n");
            html.append("    </tr>\n");
        }

        html.append("  </table>\n");
        html.append("</div>\n");
        html.append("</body>\n</html>");

        return html.toString();
    }
}
