package com.example.judicialappraisal.caseinfo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;

@Data
@TableName(value = "case_info", autoResultMap = true)
public class CaseInfo {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String caseNo;
    private String caseTitle;
    private String caseType;
    private String caseStatus;
    private String currentNodeCode;
    private String currentNodeName;
    private Long currentHandlerId;
    private String currentHandlerName;
    private Long acceptDeptId;
    private String acceptDeptName;
    private String entrustOrgName;
    private LocalDateTime deadlineTime;
    private Integer urgentFlag;
    private Integer overtimeFlag;
    
    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> formData;

    private LocalDateTime submittedTime;
    private LocalDateTime completedTime;
    private LocalDateTime createdTime;
    private LocalDateTime updatedTime;
    private Integer version;
}
