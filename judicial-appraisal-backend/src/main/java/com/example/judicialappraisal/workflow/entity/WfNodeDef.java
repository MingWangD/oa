package com.example.judicialappraisal.workflow.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("wf_node_def")
public class WfNodeDef {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long wfId;
    private String nodeCode;
    private String nodeName;
    private String nodeType;
    private String taskType;
    private String caseStatus;
    private String handlerDeptRule;
    private String handlerPostRule;
    private String handlerRoleRule;
    private Integer allowManualAssign;
    private Integer timeoutHours;
    private Integer sortNo;
    private Integer enabled;
    private LocalDateTime createdTime;
    private LocalDateTime updatedTime;
}