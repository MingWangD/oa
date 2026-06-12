package com.example.judicialappraisal.workflow.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("case_wf_instance")
public class CaseWfInstance {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long caseId;
    private Long wfId;
    private String wfCode;
    private String wfName;
    private String status;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String currentNodeCode;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String currentNodeName;
    private Long startedBy;
    private LocalDateTime startedTime;
    private LocalDateTime completedTime;
    private LocalDateTime terminatedTime;
    private Integer version;
    private LocalDateTime createdTime;
    private LocalDateTime updatedTime;
}
