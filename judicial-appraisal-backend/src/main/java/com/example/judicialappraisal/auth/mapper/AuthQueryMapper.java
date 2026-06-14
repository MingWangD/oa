package com.example.judicialappraisal.auth.mapper;

import com.example.judicialappraisal.auth.dto.CurrentUserRole;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface AuthQueryMapper {

    @Select("""
            SELECT u.id,
                   u.username,
                   u.real_name AS realName,
                   u.mobile,
                   u.email,
                   u.dept_id AS deptId,
                   d.dept_name AS deptName,
                   u.post_id AS postId,
                   p.post_name AS postName,
                   u.status
            FROM sys_user u
            LEFT JOIN sys_dept d ON d.id = u.dept_id AND d.deleted = 0
            LEFT JOIN sys_post p ON p.id = u.post_id AND p.deleted = 0
            WHERE u.id = #{userId}
              AND u.deleted = 0
            LIMIT 1
            """)
    CurrentUserBaseRow selectCurrentUserBaseById(@Param("userId") Long userId);

    @Select("""
            SELECT r.id,
                   r.role_code AS code,
                   r.role_name AS name,
                   r.data_scope AS dataScope
            FROM sys_user_role ur
            INNER JOIN sys_role r ON r.id = ur.role_id AND r.deleted = 0
            WHERE ur.user_id = #{userId}
              AND r.status = 'enabled'
            ORDER BY r.id
            """)
    List<CurrentUserRoleRow> selectRolesByUserId(@Param("userId") Long userId);

    @Select("""
            SELECT dept_id
            FROM sys_role_data_scope_dept
            WHERE role_id = #{roleId}
            ORDER BY dept_id
            """)
    List<Long> selectCustomDeptIdsByRoleId(@Param("roleId") Long roleId);

    record CurrentUserBaseRow(
            Long id,
            String username,
            String realName,
            String mobile,
            String email,
            Long deptId,
            String deptName,
            Long postId,
            String postName,
            String status
    ) {
    }

    record CurrentUserRoleRow(
            Long id,
            String code,
            String name,
            String dataScope
    ) {
    }
}
