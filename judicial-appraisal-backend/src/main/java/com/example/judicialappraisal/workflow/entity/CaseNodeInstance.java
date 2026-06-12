package com.example.judicialappraisal.workflow.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import java.time.LocalDateTime;
import java.util.Map;
import lombok.Data;

@Data
@TableName(value = "case_node_instance", autoResultMap = true)
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

    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> formData;

    private LocalDateTime createdTime;
    private LocalDateTime updatedTime;
}