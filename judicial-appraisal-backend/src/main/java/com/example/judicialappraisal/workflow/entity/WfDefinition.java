package com.example.judicialappraisal.workflow.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("wf_definition")
public class WfDefinition {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String wfCode;
    private String wfName;
    private String wfType;
    private Integer versionNo;
    private Integer enabled;
    private String remark;
    private Long createdBy;
    private LocalDateTime createdTime;
    private LocalDateTime updatedTime;
}