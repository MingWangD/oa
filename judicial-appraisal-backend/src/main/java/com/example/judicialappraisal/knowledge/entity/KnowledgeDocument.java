package com.example.judicialappraisal.knowledge.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("knowledge_document")
public class KnowledgeDocument {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long directoryId;
    private Long caseId;
    private String title;
    private String artifactCode;
    private String sourceType;
    private Long wfInstanceId;
    private String nodeCode;
    private String nodeName;
    private Long taskId;
    private Long currentFileId;
    private Integer currentVersionNo;
    private String formSnapshotJson;
    private String archiveResultJson;
    private String status;
    private Long createdBy;
    private LocalDateTime createdTime;
    private Long updatedBy;
    private LocalDateTime updatedTime;
    private Integer deleted;
}
