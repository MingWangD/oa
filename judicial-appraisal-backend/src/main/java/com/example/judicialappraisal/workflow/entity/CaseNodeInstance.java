package com.example.judicialappraisal.workflow.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("case_node_instance")
public class CaseNodeInstance {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long caseId;
    private Long wfInstanceId;
    private Long subflowInstanceId;
    private String nodeCode;
    private String nodeName;
    private String status;
    private LocalDateTime startedTime;
    private LocalDateTime completedTime;
    private Long handlerId;
    private String handlerName;
    private String resultAction;
    private String resultOpinion;
    private LocalDateTime createdTime;
    private LocalDateTime updatedTime;
}