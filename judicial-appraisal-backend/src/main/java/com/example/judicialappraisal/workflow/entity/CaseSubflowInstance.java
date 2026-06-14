package com.example.judicialappraisal.workflow.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("case_subflow_instance")
public class CaseSubflowInstance {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long caseId;
    private Long parentWfInstanceId;
    private Long parentTaskId;
    private String parentNodeCode;
    private Long wfId;
    private String wfCode;
    private String wfName;
    private String subflowType;
    private String status;
    private Long startedBy;
    private LocalDateTime startedTime;
    private LocalDateTime completedTime;
    private LocalDateTime terminatedTime;
    private String reason;
    private LocalDateTime createdTime;
    private LocalDateTime updatedTime;
}
