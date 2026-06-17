package com.example.judicialappraisal.knowledge.controller;

import com.example.judicialappraisal.common.ApiResponse;
import com.example.judicialappraisal.file.dto.FileContent;
import com.example.judicialappraisal.knowledge.dto.ArchiveNodeRequest;
import com.example.judicialappraisal.knowledge.dto.KnowledgeDirectoryDto;
import com.example.judicialappraisal.knowledge.dto.KnowledgeDocumentDto;
import com.example.judicialappraisal.knowledge.dto.KnowledgePermissionRequest;
import com.example.judicialappraisal.knowledge.service.KnowledgeService;
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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/knowledge")
public class KnowledgeController {

    private final KnowledgeService knowledgeService;

    public KnowledgeController(KnowledgeService knowledgeService) {
        this.knowledgeService = knowledgeService;
    }

    @GetMapping("/directories")
    public ApiResponse<List<KnowledgeDirectoryDto>> directories(@RequestParam(required = false) Long caseId) {
        return ApiResponse.success(knowledgeService.directories(caseId));
    }

    @GetMapping("/documents")
    public ApiResponse<List<KnowledgeDocumentDto>> documents(@RequestParam(required = false) Long directoryId,
                                                             @RequestParam(required = false) Long caseId,
                                                             @RequestParam(required = false) String keyword) {
        return ApiResponse.success(knowledgeService.documents(directoryId, caseId, keyword));
    }

    @PostMapping("/permissions")
    public ApiResponse<Void> grantPermission(@RequestBody KnowledgePermissionRequest request) {
        knowledgeService.grantPermission(request);
        return ApiResponse.success(null);
    }

    @PostMapping("/archive-node")
    public ApiResponse<KnowledgeDocumentDto> archiveNode(@RequestBody ArchiveNodeRequest request) {
        return ApiResponse.success(knowledgeService.archiveNode(request));
    }

    @GetMapping("/documents/{documentId}/download")
    public ResponseEntity<byte[]> download(@PathVariable Long documentId) {
        FileContent content = knowledgeService.downloadDocument(documentId);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(content.originalName(), StandardCharsets.UTF_8)
                        .build()
                        .toString())
                .contentType(MediaType.parseMediaType(content.contentType() == null ? MediaType.APPLICATION_OCTET_STREAM_VALUE : content.contentType()))
                .body(content.bytes());
    }

    @GetMapping("/documents/{documentId}/preview")
    public ResponseEntity<byte[]> preview(@PathVariable Long documentId) {
        FileContent content = knowledgeService.previewDocument(documentId);
        String contentType = content.contentType();
        if (contentType == null) {
            contentType = MediaType.APPLICATION_OCTET_STREAM_VALUE;
        } else if (contentType.startsWith("text/") && !contentType.toLowerCase().contains("charset")) {
            contentType = contentType + ";charset=UTF-8";
        }
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(contentType))
                .body(content.bytes());
    }
}
