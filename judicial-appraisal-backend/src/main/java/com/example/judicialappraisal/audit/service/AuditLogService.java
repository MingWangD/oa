package com.example.judicialappraisal.audit.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.judicialappraisal.audit.dto.AuditEventDto;
import com.example.judicialappraisal.audit.entity.AuditEvent;
import com.example.judicialappraisal.audit.mapper.AuditEventMapper;
import com.example.judicialappraisal.auth.dto.CurrentUserInfo;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

@Service
public class AuditLogService {

    private final AuditEventMapper auditEventMapper;

    public AuditLogService(AuditEventMapper auditEventMapper) {
        this.auditEventMapper = auditEventMapper;
    }

    public void record(String actionCode, String actionName, String bizType, Long bizId, Long caseId, String detailJson) {
        CurrentUserInfo user = currentUserOrNull();
        AuditEvent event = new AuditEvent();
        event.setActionCode(actionCode);
        event.setActionName(actionName);
        event.setBizType(bizType);
        event.setBizId(bizId);
        event.setCaseId(caseId);
        event.setOperatorId(user == null ? null : user.id());
        event.setOperatorName(user == null ? "system" : user.realName());
        event.setResultStatus("success");
        event.setDetailJson(detailJson);
        event.setOperatedTime(LocalDateTime.now());
        auditEventMapper.insert(event);
    }

    public List<AuditEventDto> list(Long caseId, String bizType, Long bizId, int limit) {
        int size = limit <= 0 ? 50 : Math.min(limit, 200);
        return auditEventMapper.selectList(new LambdaQueryWrapper<AuditEvent>()
                        .eq(caseId != null, AuditEvent::getCaseId, caseId)
                        .eq(bizType != null && !bizType.isBlank(), AuditEvent::getBizType, bizType)
                        .eq(bizId != null, AuditEvent::getBizId, bizId)
                        .orderByDesc(AuditEvent::getOperatedTime)
                        .orderByDesc(AuditEvent::getId)
                        .last("limit " + size))
                .stream()
                .map(this::toDto)
                .toList();
    }

    private AuditEventDto toDto(AuditEvent event) {
        return new AuditEventDto(
                event.getId(),
                event.getActionCode(),
                event.getActionName(),
                event.getBizType(),
                event.getBizId(),
                event.getCaseId(),
                event.getOperatorId(),
                event.getOperatorName(),
                event.getResultStatus(),
                event.getDetailJson(),
                event.getOperatedTime()
        );
    }

    private CurrentUserInfo currentUserOrNull() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof CurrentUserInfo userInfo)) {
            return null;
        }
        return userInfo;
    }
}
