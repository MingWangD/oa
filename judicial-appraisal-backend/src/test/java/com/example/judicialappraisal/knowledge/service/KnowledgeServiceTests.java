package com.example.judicialappraisal.knowledge.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.judicialappraisal.audit.service.AuditLogService;
import com.example.judicialappraisal.caseinfo.entity.CaseInfo;
import com.example.judicialappraisal.caseinfo.mapper.CaseInfoMapper;
import com.example.judicialappraisal.file.service.FileStorageService;
import com.example.judicialappraisal.knowledge.dto.ArchiveNodeRequest;
import com.example.judicialappraisal.knowledge.dto.KnowledgeDocumentDto;
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
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class KnowledgeServiceTests {

    private final KnowledgeDirectoryMapper directoryMapper = mock(KnowledgeDirectoryMapper.class);
    private final KnowledgeDocumentMapper documentMapper = mock(KnowledgeDocumentMapper.class);
    private final KnowledgeDocumentVersionMapper versionMapper = mock(KnowledgeDocumentVersionMapper.class);
    private final KnowledgePermissionMapper permissionMapper = mock(KnowledgePermissionMapper.class);
    private final CaseArchiveRecordMapper archiveRecordMapper = mock(CaseArchiveRecordMapper.class);
    private final CaseInfoMapper caseInfoMapper = mock(CaseInfoMapper.class);
    private final FileStorageService fileStorageService = mock(FileStorageService.class);
    private final AuditLogService auditLogService = mock(AuditLogService.class);
    private final KnowledgeService service = new KnowledgeService(
            directoryMapper,
            documentMapper,
            versionMapper,
            permissionMapper,
            archiveRecordMapper,
            caseInfoMapper,
            fileStorageService,
            auditLogService,
            new ObjectMapper()
    );

    @Test
    void archiveNodeCreatesDocumentVersionAndArchiveRecord() {
        CaseInfo caseInfo = new CaseInfo();
        caseInfo.setId(88L);
        caseInfo.setCaseNo("JA-88");
        when(caseInfoMapper.selectById(88L)).thenReturn(caseInfo);
        when(directoryMapper.selectOne(any())).thenReturn((KnowledgeDirectory) null);
        when(documentMapper.selectOne(any())).thenReturn(null);
        doAnswer(invocation -> {
            KnowledgeDirectory directory = invocation.getArgument(0);
            directory.setId(directory.getDirectoryCode().equals("case-archive") ? 1L : 2L);
            return 1;
        }).when(directoryMapper).insert(any(KnowledgeDirectory.class));
        doAnswer(invocation -> {
            KnowledgeDocument document = invocation.getArgument(0);
            document.setId(100L);
            return 1;
        }).when(documentMapper).insert(any(KnowledgeDocument.class));

        KnowledgeDocumentDto dto = service.archiveNode(new ArchiveNodeRequest(
                88L,
                501L,
                "FINAL_REVIEW",
                "送审稿审核",
                701L,
                "送审稿审核归档",
                "final-review",
                Map.of("result", "通过"),
                List.of(3001L),
                "审核通过"
        ));

        assertThat(dto.id()).isEqualTo(100L);
        ArgumentCaptor<KnowledgeDocumentVersion> versionCaptor = ArgumentCaptor.forClass(KnowledgeDocumentVersion.class);
        verify(versionMapper).insert(versionCaptor.capture());
        assertThat(versionCaptor.getValue().getVersionNo()).isEqualTo(1);
        assertThat(versionCaptor.getValue().getFileId()).isEqualTo(3001L);
        verify(archiveRecordMapper).insert(any(CaseArchiveRecord.class));
        verify(auditLogService).record(any(), any(), any(), any(), any(), any());
    }

    @Test
    void documentsSupportFullTextSearchAcrossSnapshotsAndFileContent() {
        KnowledgeDocument document = new KnowledgeDocument();
        document.setId(9L);
        document.setDirectoryId(1L);
        document.setCaseId(88L);
        document.setTitle("普通标题");
        document.setArtifactCode("artifact-1");
        document.setNodeName("送审节点");
        document.setFormSnapshotJson("{\"remark\":\"关键字在表单快照里\"}");
        document.setArchiveResultJson("{\"summary\":\"归档结果\"}");
        document.setCurrentFileId(3001L);
        KnowledgePermission permission = new KnowledgePermission();
        permission.setSubjectType("all");
        permission.setPermissionCode("read");
        when(directoryMapper.selectList(any())).thenReturn(List.of());
        when(documentMapper.selectList(any())).thenReturn(List.of(document));
        when(permissionMapper.selectList(any())).thenReturn(List.of(permission));
        when(fileStorageService.extractText(3001L)).thenReturn("文件全文里还有另一个关键短语");

        List<KnowledgeDocumentDto> matchedBySnapshot = service.documents(null, 88L, "关键字在表单快照里");
        List<KnowledgeDocumentDto> matchedByFile = service.documents(null, 88L, "关键短语");

        assertThat(matchedBySnapshot).hasSize(1);
        assertThat(matchedByFile).hasSize(1);
    }
}
