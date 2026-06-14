package com.example.judicialappraisal.knowledge.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("knowledge_permission")
public class KnowledgePermission {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long directoryId;
    private Long documentId;
    private String subjectType;
    private Long subjectId;
    private String permissionCode;
    private Long createdBy;
    private LocalDateTime createdTime;
}
