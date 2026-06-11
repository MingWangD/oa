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
    private String formCode;
    private Integer versionNo;
    private Integer enabled;
    private String publishStatus;
    private String remark;
    private String definitionJson;
    private Long sourceWfId;
    private Long publishedBy;
    private LocalDateTime publishedTime;
    private Integer immutableFlag;
    private Long createdBy;
    private LocalDateTime createdTime;
    private Long updatedBy;
    private LocalDateTime updatedTime;
    private Integer deleted;
}
