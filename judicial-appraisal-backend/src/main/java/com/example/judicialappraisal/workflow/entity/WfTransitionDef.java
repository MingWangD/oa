package com.example.judicialappraisal.workflow.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("wf_transition_def")
public class WfTransitionDef {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long wfId;
    private String fromNodeCode;
    private String toNodeCode;
    private String actionCode;
    private String actionName;
    private Integer requireReason;
    private Integer requireOpinion;
    private Integer enabled;
    private Integer sortNo;
    private LocalDateTime createdTime;
    private LocalDateTime updatedTime;
}