package com.example.judicialappraisal.workflow.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("case_task")
public class CaseTask {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long caseId;
    private Long wfInstanceId;
    private Long subflowInstanceId;
    private Long nodeInstanceId;
    private String taskType;
    private String taskTitle;
    private String nodeCode;
    private String nodeName;
    private String status;
    private Long assigneeId;
    private String assigneeName;
    private Long claimedBy;
    private LocalDateTime claimedTime;
    private LocalDateTime startedTime;
    private LocalDateTime completedTime;
    private LocalDateTime deadlineTime;
    private Integer overtimeFlag;
    private String resultAction;
    private String resultOpinion;
}
