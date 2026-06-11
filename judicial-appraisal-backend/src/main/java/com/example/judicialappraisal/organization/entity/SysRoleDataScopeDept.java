package com.example.judicialappraisal.organization.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("sys_role_data_scope_dept")
public class SysRoleDataScopeDept {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long roleId;
    private Long deptId;
    private LocalDateTime createdTime;
}
