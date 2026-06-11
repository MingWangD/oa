package com.example.judicialappraisal.file.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("file_version")
public class FileVersion {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String bizType;
    private Long bizId;
    private Long caseId;
    private String nodeCode;
    private Long taskId;
    private String artifactCode;
    private String artifactName;
    private Integer versionNo;
    private Long fileId;
    private String changeNote;
    private Long createdBy;
    private LocalDateTime createdTime;
}
