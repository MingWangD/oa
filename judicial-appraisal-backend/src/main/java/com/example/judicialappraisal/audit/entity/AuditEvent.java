package com.example.judicialappraisal.audit.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("audit_event")
public class AuditEvent {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String actionCode;
    private String actionName;
    private String bizType;
    private Long bizId;
    private Long caseId;
    private Long operatorId;
    private String operatorName;
    private String resultStatus;
    private String detailJson;
    private String ipAddress;
    private String userAgent;
    private LocalDateTime operatedTime;
}
