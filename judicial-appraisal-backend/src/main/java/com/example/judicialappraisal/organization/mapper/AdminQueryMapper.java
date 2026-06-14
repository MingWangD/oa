package com.example.judicialappraisal.organization.mapper;

import com.example.judicialappraisal.organization.dto.OrganizationDeptDto;
import com.example.judicialappraisal.organization.dto.OrganizationPostDto;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface AdminQueryMapper {

    @Select("""
            <script>
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
            WHERE u.deleted = 0
            <if test="keyword != null and keyword != ''">
              AND (u.username LIKE CONCAT('%', #{keyword}, '%')
                   OR u.real_name LIKE CONCAT('%', #{keyword}, '%')
                   OR u.mobile LIKE CONCAT('%', #{keyword}, '%'))
            </if>
            ORDER BY u.id DESC
            </script>
            """)
    List<AdminUserRow> selectUsers(@Param("keyword") String keyword);

    @Select("""
            <script>
            SELECT ur.user_id AS userId,
                   r.id,
                   r.role_code AS roleCode,
                   r.role_name AS roleName,
                   r.status
            FROM sys_user_role ur
            INNER JOIN sys_role r ON r.id = ur.role_id AND r.deleted = 0
            WHERE ur.user_id IN
            <foreach collection="userIds" item="userId" open="(" separator="," close=")">
              #{userId}
            </foreach>
            ORDER BY ur.user_id, r.id
            </script>
            """)
    List<UserRoleRow> selectRolesByUserIds(@Param("userIds") List<Long> userIds);

    @Select("""
            SELECT id,
                   role_code AS roleCode,
                   role_name AS roleName,
                   status,
                   data_scope AS dataScope
            FROM sys_role
            WHERE deleted = 0
            ORDER BY id
            """)
    List<RoleRow> selectRoles();

    @Select("""
            SELECT id,
                   parent_id AS parentId,
                   dept_name AS deptName,
                   dept_code AS deptCode,
                   sort_no AS sortNo,
                   status
            FROM sys_dept
            WHERE deleted = 0
            ORDER BY sort_no, id
            """)
    List<OrganizationDeptDto> selectDepts();

    @Select("""
            SELECT id,
                   post_name AS postName,
                   post_code AS postCode,
                   sort_no AS sortNo,
                   status
            FROM sys_post
            WHERE deleted = 0
            ORDER BY sort_no, id
            """)
    List<OrganizationPostDto> selectPosts();

    record AdminUserRow(
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

    record RoleRow(Long id, String roleCode, String roleName, String status, String dataScope) {
    }

    record UserRoleRow(Long userId, Long id, String roleCode, String roleName, String status) {
    }
}
