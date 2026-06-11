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
            ensureBucket();
            String originalName = multipartFile.getOriginalFilename() == null ? "unknown" : multipartFile.getOriginalFilename();
            String ext = fileExt(originalName);
            String objectName = LocalDateTime.now().toLocalDate() + "/" + UUID.randomUUID() + (ext.isBlank() ? "" : "." + ext);
            byte[] bytes = multipartFile.getBytes();
            String md5 = md5(bytes);
            minioClient.putObject(PutObjectArgs.builder()
                    .bucket(bucket)
                    .object(objectName)
                    .contentType(multipartFile.getContentType())
                    .stream(multipartFile.getInputStream(), multipartFile.getSize(), -1)
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
            return new FileUploadResponse(file.getId(), originalName, multipartFile.getContentType(), multipartFile.getSize(), md5, versionNo);
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BusinessException("文件上传失败：" + ex.getMessage());
        }
    }

    public FileContent download(Long fileId) {
        SysFile file = requireFile(fileId);
        try (InputStream inputStream = minioClient.getObject(GetObjectArgs.builder()
                .bucket(file.getStorageBucket())
                .object(file.getStorageKey())
                .build());
             ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            inputStream.transferTo(outputStream);
            auditLogService.record("FILE_DOWNLOAD", "文件下载", "file", fileId, null,
                    "{\"originalName\":\"" + escape(file.getOriginalName()) + "\"}");
            return new FileContent(file.getOriginalName(), file.getContentType(), outputStream.toByteArray());
        } catch (Exception ex) {
            throw new BusinessException("文件下载失败：" + ex.getMessage());
        }
    }

    public FileContent preview(Long fileId) {
        SysFile file = requireFile(fileId);
        FileContent content = download(fileId);
        auditLogService.record("FILE_PREVIEW", "文件预览", "file", fileId, null,
                "{\"originalName\":\"" + escape(file.getOriginalName()) + "\"}");
        return content;
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
}
