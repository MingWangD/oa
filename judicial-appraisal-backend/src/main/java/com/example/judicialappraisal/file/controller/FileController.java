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
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(content.originalName(), StandardCharsets.UTF_8)
                        .build()
                        .toString())
                .contentType(MediaType.parseMediaType(content.contentType() == null ? MediaType.APPLICATION_OCTET_STREAM_VALUE : content.contentType()))
                .body(content.bytes());
    }

    @GetMapping("/{fileId}/preview")
    public ResponseEntity<byte[]> preview(@PathVariable Long fileId) {
        FileContent content = fileStorageService.preview(fileId);
        String contentType = content.contentType();
        byte[] bodyBytes = content.bytes();
        
        String originalName = content.originalName();
        String ext = "";
        if (originalName != null && originalName.lastIndexOf('.') >= 0) {
            ext = originalName.substring(originalName.lastIndexOf('.') + 1).toLowerCase();
        }
        boolean isText = (contentType != null && contentType.startsWith("text/")) 
                || java.util.List.of("txt", "md", "csv", "json", "xml", "log").contains(ext);
        boolean isHtml = (contentType != null && contentType.toLowerCase().contains("text/html"))
                || "html".equals(ext);

        if (isText && !isHtml) {
            String text = new String(bodyBytes, java.nio.charset.StandardCharsets.UTF_8);
            String escapedText = text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
            String html = "<html><head><meta charset=\"utf-8\"><title>" + (originalName == null ? "Preview" : originalName) + "</title></head>"
                    + "<body style=\"font-family: monospace; white-space: pre-wrap; padding: 20px; background: #fafafa;\">"
                    + "<pre style=\"margin: 0; word-break: break-all;\">" + escapedText + "</pre></body></html>";
            bodyBytes = html.getBytes(java.nio.charset.StandardCharsets.UTF_8);
            contentType = "text/html;charset=UTF-8";
        } else if (contentType == null) {
            contentType = MediaType.APPLICATION_OCTET_STREAM_VALUE;
        } else if (contentType.startsWith("text/") && !contentType.toLowerCase().contains("charset")) {
            contentType = contentType + ";charset=UTF-8";
        }

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(contentType))
                .body(bodyBytes);
    }

    @GetMapping("/versions")
    public ApiResponse<List<FileVersionDto>> versions(@RequestParam String bizType,
                                                      @RequestParam(required = false) Long bizId,
                                                      @RequestParam(required = false) String artifactCode) {
        return ApiResponse.success(fileStorageService.listVersions(bizType, bizId, artifactCode));
    }
}
