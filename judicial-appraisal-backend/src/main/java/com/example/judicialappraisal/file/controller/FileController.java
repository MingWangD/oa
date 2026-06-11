package com.example.judicialappraisal.file.controller;

import com.example.judicialappraisal.common.ApiResponse;
import com.example.judicialappraisal.file.dto.FileContent;
import com.example.judicialappraisal.file.dto.FileUploadResponse;
import com.example.judicialappraisal.file.dto.FileVersionDto;
import com.example.judicialappraisal.file.service.FileStorageService;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/files")
public class FileController {

    private final FileStorageService fileStorageService;

    public FileController(FileStorageService fileStorageService) {
        this.fileStorageService = fileStorageService;
    }

    @GetMapping("/health")
    public ApiResponse<String> health() {
        return ApiResponse.success("file ok");
    }

    @PostMapping("/upload")
    public ApiResponse<FileUploadResponse> upload(@RequestParam("file") MultipartFile file,
                                                  @RequestParam(required = false) String bizType,
                                                  @RequestParam(required = false) Long bizId,
                                                  @RequestParam(required = false) Long caseId,
                                                  @RequestParam(required = false) String nodeCode,
                                                  @RequestParam(required = false) Long taskId,
                                                  @RequestParam(required = false) String artifactCode,
                                                  @RequestParam(required = false) String artifactName,
                                                  @RequestParam(required = false) String changeNote) {
        return ApiResponse.success(fileStorageService.upload(file, bizType, bizId, caseId, nodeCode, taskId, artifactCode, artifactName, changeNote));
    }

    @GetMapping("/{fileId}/download")
    public ResponseEntity<byte[]> download(@PathVariable Long fileId) {
        FileContent content = fileStorageService.download(fileId);
        String encodedName = URLEncoder.encode(content.originalName(), StandardCharsets.UTF_8).replace("+", "%20");
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(encodedName, StandardCharsets.UTF_8)
                        .build()
                        .toString())
                .contentType(MediaType.parseMediaType(content.contentType() == null ? MediaType.APPLICATION_OCTET_STREAM_VALUE : content.contentType()))
                .body(content.bytes());
    }

    @GetMapping("/{fileId}/preview")
    public ResponseEntity<byte[]> preview(@PathVariable Long fileId) {
        FileContent content = fileStorageService.preview(fileId);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(content.contentType() == null ? MediaType.APPLICATION_OCTET_STREAM_VALUE : content.contentType()))
                .body(content.bytes());
    }

    @GetMapping("/versions")
    public ApiResponse<List<FileVersionDto>> versions(@RequestParam String bizType,
                                                      @RequestParam(required = false) Long bizId,
                                                      @RequestParam(required = false) String artifactCode) {
        return ApiResponse.success(fileStorageService.listVersions(bizType, bizId, artifactCode));
    }
}
