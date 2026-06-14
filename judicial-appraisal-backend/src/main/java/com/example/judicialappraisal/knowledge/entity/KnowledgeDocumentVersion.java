package com.example.judicialappraisal.knowledge.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("knowledge_document_version")
public class KnowledgeDocumentVersion {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long documentId;
    private Integer versionNo;
    private Long fileId;
    private String formSnapshotJson;
    private String archiveResultJson;
    private String changeNote;
    private Long createdBy;
    private LocalDateTime createdTime;
}
