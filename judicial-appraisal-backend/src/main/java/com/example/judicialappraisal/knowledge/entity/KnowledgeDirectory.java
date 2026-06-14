package com.example.judicialappraisal.knowledge.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("knowledge_directory")
public class KnowledgeDirectory {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long parentId;
    private String directoryCode;
    private String directoryName;
    private String directoryType;
    private Long caseId;
    private Long deptId;
    private Long ownerUserId;
    private String path;
    private Integer sortNo;
    private Long createdBy;
    private LocalDateTime createdTime;
    private Long updatedBy;
    private LocalDateTime updatedTime;
    private Integer deleted;
}
