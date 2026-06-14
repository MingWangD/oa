package com.example.judicialappraisal.workflow.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("case_task_candidate")
public class CaseTaskCandidate {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long taskId;
    private Long caseId;
    private Long candidateUserId;
    private Long candidateDeptId;
    private Long candidatePostId;
    private Long candidateRoleId;
    private LocalDateTime createdTime;
}
