package com.example.judicialappraisal.audit.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.judicialappraisal.audit.entity.AuditEvent;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface AuditEventMapper extends BaseMapper<AuditEvent> {
}
