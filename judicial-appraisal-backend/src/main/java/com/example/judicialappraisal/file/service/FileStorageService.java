package com.example.judicialappraisal.file.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.judicialappraisal.audit.service.AuditLogService;
import com.example.judicialappraisal.auth.dto.CurrentUserInfo;
import com.example.judicialappraisal.common.exception.BusinessException;
import com.example.judicialappraisal.file.dto.FileContent;
import com.example.judicialappraisal.file.dto.FileUploadResponse;
import com.example.judicialappraisal.file.dto.FileVersionDto;
import com.example.judicialappraisal.file.entity.FileVersion;
import com.example.judicialappraisal.file.entity.SysFile;
import com.example.judicialappraisal.file.mapper.FileVersionMapper;
import com.example.judicialappraisal.file.mapper.SysFileMapper;
import io.minio.BucketExistsArgs;
import io.minio.GetObjectArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class FileStorageService {
    private static final String VIRUS_SCAN_CLEAN = "clean";
    private static final String VIRUS_SCAN_INFECTED = "infected";
    private static final int SEARCH_TEXT_LIMIT = 64 * 1024;

    private final MinioClient minioClient;
    private final SysFileMapper sysFileMapper;
    private final FileVersionMapper fileVersionMapper;
    private final AuditLogService auditLogService;
    private final String bucket;

    public FileStorageService(MinioClient minioClient,
                              SysFileMapper sysFileMapper,
                              FileVersionMapper fileVersionMapper,
                              AuditLogService auditLogService,
                              @Value("${app.minio.bucket}") String bucket) {
        this.minioClient = minioClient;
        this.sysFileMapper = sysFileMapper;
        this.fileVersionMapper = fileVersionMapper;
        this.auditLogService = auditLogService;
        this.bucket = bucket;
    }

    @Transactional
    public FileUploadResponse upload(MultipartFile multipartFile,
                                     String bizType,
                                     Long bizId,
                                     Long caseId,
                                     String nodeCode,
                                     Long taskId,
                                     String artifactCode,
                                     String artifactName,
                                     String changeNote) {
        if (multipartFile == null || multipartFile.isEmpty()) {
            throw new BusinessException("上传文件不能为空");
        }
        try {
            String originalName = multipartFile.getOriginalFilename() == null ? "unknown" : multipartFile.getOriginalFilename();
            String ext = fileExt(originalName);
            byte[] bytes = multipartFile.getBytes();
            String virusScanStatus = scanVirus(bytes, originalName, multipartFile.getContentType());
            if (!VIRUS_SCAN_CLEAN.equals(virusScanStatus)) {
                throw new BusinessException("文件病毒扫描未通过");
            }
            ensureBucket();
            String objectName = LocalDateTime.now().toLocalDate() + "/" + UUID.randomUUID() + (ext.isBlank() ? "" : "." + ext);
            String md5 = md5(bytes);
            List<SysFile> duplicates = findDuplicates(md5);
            SysFile duplicateOf = duplicates.stream().findFirst().orElse(null);
            minioClient.putObject(PutObjectArgs.builder()
                    .bucket(bucket)
                    .object(objectName)
                    .contentType(multipartFile.getContentType())
                    .stream(new ByteArrayInputStream(bytes), bytes.length, -1)
                    .build());

            CurrentUserInfo user = currentUserOrNull();
            SysFile file = new SysFile();
            file.setOriginalName(originalName);
            file.setFileName(objectName.substring(objectName.lastIndexOf('/') + 1));
            file.setFileExt(ext);
            file.setContentType(multipartFile.getContentType());
            file.setFileSize(multipartFile.getSize());
            file.setStorageBucket(bucket);
            file.setStorageKey(objectName);
            file.setMd5(md5);
            file.setUploadUserId(user == null ? null : user.id());
            file.setUploadUserName(user == null ? "system" : user.realName());
            file.setCreatedTime(LocalDateTime.now());
            file.setDeleted(0);
            sysFileMapper.insert(file);

            Integer versionNo = null;
            if (bizType != null && !bizType.isBlank()) {
                versionNo = createVersion(file.getId(), bizType, bizId, caseId, nodeCode, taskId, artifactCode, artifactName, changeNote);
            }
            auditLogService.record("FILE_UPLOAD", "文件上传", "file", file.getId(), caseId,
                    "{\"originalName\":\"" + escape(originalName) + "\",\"bizType\":\"" + escape(bizType) + "\"}");
            if (duplicateOf != null) {
                auditLogService.record("FILE_DUPLICATE_DETECTED", "文件查重命中", "file", file.getId(), caseId,
                        "{\"md5\":\"" + md5 + "\",\"duplicateOfFileId\":" + duplicateOf.getId() + "}");
            }
            return new FileUploadResponse(
                    file.getId(),
                    originalName,
                    multipartFile.getContentType(),
                    multipartFile.getSize(),
                    md5,
                    versionNo,
                    duplicateOf != null,
                    duplicateOf == null ? null : duplicateOf.getId(),
                    duplicates.size(),
                    virusScanStatus,
                    supportsWatermark(multipartFile.getContentType(), originalName)
            );
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BusinessException("文件上传失败：" + ex.getMessage());
        }
    }

    public FileContent download(Long fileId) {
        SysFile file = requireFile(fileId);
        FileContent content = loadContent(file, false, false);
        auditLogService.record("FILE_DOWNLOAD", "文件下载", "file", fileId, null,
                "{\"originalName\":\"" + escape(file.getOriginalName()) + "\"}");
        return content;
    }

    public FileContent preview(Long fileId) {
        SysFile file = requireFile(fileId);
        FileContent content = loadContent(file, true, false);
        auditLogService.record("FILE_PREVIEW", "文件预览", "file", fileId, null,
                "{\"originalName\":\"" + escape(file.getOriginalName()) + "\"}");
        return content;
    }

    public String extractText(Long fileId) {
        SysFile file = requireFile(fileId);
        return extractText(loadContent(file, false, true));
    }

    private FileContent loadContent(SysFile file, boolean watermark, boolean suppressAudit) {
        try (InputStream inputStream = minioClient.getObject(GetObjectArgs.builder()
                .bucket(file.getStorageBucket())
                .object(file.getStorageKey())
                .build());
             ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            inputStream.transferTo(outputStream);
            byte[] contentBytes = outputStream.toByteArray();
            if (watermark && supportsWatermark(file.getContentType(), file.getOriginalName())) {
                contentBytes = applyWatermark(contentBytes, file);
            }
            return new FileContent(file.getOriginalName(), file.getContentType(), contentBytes);
        } catch (Exception ex) {
            throw new BusinessException("文件下载失败：" + ex.getMessage());
        }
    }

    public List<FileVersionDto> listVersions(String bizType, Long bizId, String artifactCode) {
        return fileVersionMapper.selectList(new LambdaQueryWrapper<FileVersion>()
                        .eq(FileVersion::getBizType, bizType)
                        .eq(bizId != null, FileVersion::getBizId, bizId)
                        .eq(artifactCode != null && !artifactCode.isBlank(), FileVersion::getArtifactCode, artifactCode)
                        .orderByDesc(FileVersion::getVersionNo)
                        .orderByDesc(FileVersion::getId))
                .stream()
                .map(this::toDto)
                .toList();
    }

    private Integer createVersion(Long fileId, String bizType, Long bizId, Long caseId, String nodeCode, Long taskId,
                                  String artifactCode, String artifactName, String changeNote) {
        Integer latest = fileVersionMapper.selectList(new LambdaQueryWrapper<FileVersion>()
                        .eq(FileVersion::getBizType, bizType)
                        .eq(bizId != null, FileVersion::getBizId, bizId)
                        .eq(artifactCode != null && !artifactCode.isBlank(), FileVersion::getArtifactCode, artifactCode)
                        .orderByDesc(FileVersion::getVersionNo)
                        .last("limit 1"))
                .stream()
                .findFirst()
                .map(FileVersion::getVersionNo)
                .orElse(0);
        FileVersion version = new FileVersion();
        version.setBizType(bizType);
        version.setBizId(bizId);
        version.setCaseId(caseId);
        version.setNodeCode(nodeCode);
        version.setTaskId(taskId);
        version.setArtifactCode(artifactCode);
        version.setArtifactName(artifactName);
        version.setVersionNo(latest + 1);
        version.setFileId(fileId);
        version.setChangeNote(changeNote);
        CurrentUserInfo user = currentUserOrNull();
        version.setCreatedBy(user == null ? null : user.id());
        version.setCreatedTime(LocalDateTime.now());
        fileVersionMapper.insert(version);
        return version.getVersionNo();
    }

    private SysFile requireFile(Long fileId) {
        SysFile file = sysFileMapper.selectById(fileId);
        if (file == null || Integer.valueOf(1).equals(file.getDeleted())) {
            throw new BusinessException("文件不存在");
        }
        return file;
    }

    private void ensureBucket() throws Exception {
        boolean exists = minioClient.bucketExists(BucketExistsArgs.builder().bucket(bucket).build());
        if (!exists) {
            minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
        }
    }

    private String md5(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("MD5").digest(bytes));
    }

    private List<SysFile> findDuplicates(String md5) {
        return sysFileMapper.selectList(new LambdaQueryWrapper<SysFile>()
                .eq(SysFile::getMd5, md5)
                .eq(SysFile::getDeleted, 0)
                .orderByAsc(SysFile::getId));
    }

    private String scanVirus(byte[] bytes, String originalName, String contentType) {
        String text = new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
        String lowerName = originalName == null ? "" : originalName.toLowerCase(java.util.Locale.ROOT);
        if (text.contains("X5O!P%@AP[4\\PZX54(P^)7CC)7}$EICAR-STANDARD-ANTIVIRUS-TEST-FILE!$H+H*")) {
            return VIRUS_SCAN_INFECTED;
        }
        if (lowerName.endsWith(".exe") || lowerName.endsWith(".bat") || lowerName.endsWith(".cmd") || lowerName.endsWith(".js")) {
            return VIRUS_SCAN_INFECTED;
        }
        if (contentType != null && contentType.contains("x-msdownload")) {
            return VIRUS_SCAN_INFECTED;
        }
        return VIRUS_SCAN_CLEAN;
    }

    private boolean supportsWatermark(String contentType, String originalName) {
        if (contentType != null) {
            return contentType.startsWith("text/")
                    || contentType.contains("json")
                    || contentType.contains("xml")
                    || contentType.contains("csv");
        }
        String ext = fileExt(originalName == null ? "" : originalName);
        return List.of("txt", "md", "csv", "json", "xml", "html", "log").contains(ext);
    }

    private byte[] applyWatermark(byte[] bytes, SysFile file) {
        String body = new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
        String watermark = "\n\n[Preview Watermark] " + valueOrDefault(file.getUploadUserName(), "system")
                + " / " + valueOrDefault(file.getOriginalName(), "file")
                + " / " + valueOrDefault(file.getCreatedTime() == null ? null : file.getCreatedTime().toString(), LocalDateTime.now().toString());
        return (body + watermark).getBytes(java.nio.charset.StandardCharsets.UTF_8);
    }

    private String extractText(FileContent content) {
        if (!supportsWatermark(content.contentType(), content.originalName())) {
            return "";
        }
        byte[] bytes = content.bytes();
        int length = Math.min(bytes.length, SEARCH_TEXT_LIMIT);
        return new String(bytes, 0, length, java.nio.charset.StandardCharsets.UTF_8);
    }

    private String fileExt(String originalName) {
        int index = originalName.lastIndexOf('.');
        return index < 0 || index == originalName.length() - 1 ? "" : originalName.substring(index + 1).toLowerCase();
    }

    private FileVersionDto toDto(FileVersion version) {
        return new FileVersionDto(
                version.getId(),
                version.getBizType(),
                version.getBizId(),
                version.getCaseId(),
                version.getArtifactCode(),
                version.getArtifactName(),
                version.getVersionNo(),
                version.getFileId(),
                version.getChangeNote(),
                version.getCreatedTime()
        );
    }

    private CurrentUserInfo currentUserOrNull() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof CurrentUserInfo userInfo)) {
            return null;
        }
        return userInfo;
    }

    private String escape(String value) {
        return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private String valueOrDefault(String value, String defaultValue) {
        return value == null || value.isBlank() ? defaultValue : value;
    }
}
