package com.example.judicialappraisal.file.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.example.judicialappraisal.audit.service.AuditLogService;
import com.example.judicialappraisal.common.exception.BusinessException;
import com.example.judicialappraisal.file.dto.FileContent;
import com.example.judicialappraisal.file.dto.FileUploadResponse;
import com.example.judicialappraisal.file.entity.FileVersion;
import com.example.judicialappraisal.file.entity.SysFile;
import com.example.judicialappraisal.file.mapper.FileVersionMapper;
import com.example.judicialappraisal.file.mapper.SysFileMapper;
import io.minio.BucketExistsArgs;
import io.minio.GetObjectArgs;
import io.minio.GetObjectResponse;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

class FileStorageServiceTests {

    private final MinioClient minioClient = mock(MinioClient.class);
    private final SysFileMapper sysFileMapper = mock(SysFileMapper.class);
    private final FileVersionMapper fileVersionMapper = mock(FileVersionMapper.class);
    private final AuditLogService auditLogService = mock(AuditLogService.class);
    private final FileStorageService service = new FileStorageService(
            minioClient,
            sysFileMapper,
            fileVersionMapper,
            auditLogService,
            "unit-test-bucket"
    );

    @Test
    void uploadMarksDuplicateAndEnablesPreviewWatermarkForTextFiles() throws Exception {
        when(minioClient.bucketExists(any(BucketExistsArgs.class))).thenReturn(true);
        SysFile duplicate = new SysFile();
        duplicate.setId(7L);
        duplicate.setMd5("duplicate-md5");
        when(sysFileMapper.selectList(any())).thenReturn(List.of(duplicate));
        doAnswer(invocation -> {
            SysFile file = invocation.getArgument(0);
            file.setId(11L);
            return 1;
        }).when(sysFileMapper).insert(any(SysFile.class));
        doAnswer(invocation -> {
            FileVersion version = invocation.getArgument(0);
            version.setId(21L);
            return 1;
        }).when(fileVersionMapper).insert(any(FileVersion.class));

        MockMultipartFile multipartFile = new MockMultipartFile(
                "file",
                "note.txt",
                "text/plain",
                "hello world".getBytes(StandardCharsets.UTF_8)
        );

        FileUploadResponse response = service.upload(multipartFile, "knowledge", 1L, 2L, "NODE", 3L, "artifact", "说明", "首次上传");

        assertThat(response.duplicate()).isTrue();
        assertThat(response.duplicateOfFileId()).isEqualTo(7L);
        assertThat(response.duplicateCount()).isEqualTo(1);
        assertThat(response.virusScanStatus()).isEqualTo("clean");
        assertThat(response.previewWatermarkEnabled()).isTrue();
    }

    @Test
    void uploadRejectsVirusSignature() {
        MockMultipartFile multipartFile = new MockMultipartFile(
                "file",
                "virus.txt",
                "text/plain",
                "X5O!P%@AP[4\\PZX54(P^)7CC)7}$EICAR-STANDARD-ANTIVIRUS-TEST-FILE!$H+H*".getBytes(StandardCharsets.UTF_8)
        );

        assertThatThrownBy(() -> service.upload(multipartFile, null, null, null, null, null, null, null, null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("病毒扫描");
    }

    @Test
    void previewAppendsWatermarkForTextFiles() throws Exception {
        SysFile file = new SysFile();
        file.setId(99L);
        file.setOriginalName("memo.txt");
        file.setContentType("text/plain");
        file.setStorageBucket("bucket");
        file.setStorageKey("key");
        file.setUploadUserName("tester");
        file.setCreatedTime(java.time.LocalDateTime.of(2026, 6, 11, 12, 0));
        when(sysFileMapper.selectById(99L)).thenReturn(file);
        ByteArrayInputStream delegate = new ByteArrayInputStream("plain body".getBytes(StandardCharsets.UTF_8));
        GetObjectResponse response = mock(GetObjectResponse.class);
        when(response.read(any(byte[].class), anyInt(), anyInt())).thenAnswer(invocation ->
                delegate.read(invocation.getArgument(0), invocation.getArgument(1), invocation.getArgument(2)));
        when(response.read()).thenAnswer(invocation -> delegate.read());
        when(minioClient.getObject(any(GetObjectArgs.class))).thenReturn(response);

        FileContent preview = service.preview(99L);

        assertThat(new String(preview.bytes(), StandardCharsets.UTF_8))
                .contains("[Preview Watermark]")
                .contains("tester");
    }
}
