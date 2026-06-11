package com.example.judicialappraisal.knowledge.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("case_archive_record")
public class CaseArchiveRecord {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long caseId;
    private String caseNo;
    private Long wfInstanceId;
    private String nodeCode;
    private String nodeName;
    private Long taskId;
    private Long documentId;
    private String archiveType;
    private String archiveStatus;
    private String archiveSummary;
    private Long archivedBy;
    private LocalDateTime archivedTime;
}
