package com.example.judicialappraisal.organization.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.judicialappraisal.organization.entity.SysUserRole;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface SysUserRoleMapper extends BaseMapper<SysUserRole> {

    record UserRoleCandidateRow(Long userId, Long roleId) {
    }

    @Select("""
            SELECT r.id
            FROM sys_user_role ur
            INNER JOIN sys_role r ON r.id = ur.role_id
            WHERE ur.user_id = #{userId}
              AND r.deleted = 0
              AND r.status = 'enabled'
            ORDER BY r.id
            """)
    List<Long> selectEnabledRoleIdsByUserId(@Param("userId") Long userId);

    @Select("""
            <script>
            SELECT DISTINCT ur.user_id AS userId,
                            ur.role_id AS roleId
            FROM sys_user_role ur
            INNER JOIN sys_role r ON r.id = ur.role_id
            WHERE ur.role_id IN
            <foreach collection="roleIds" item="roleId" open="(" separator="," close=")">
              #{roleId}
            </foreach>
              AND r.deleted = 0
              AND r.status = 'enabled'
            ORDER BY ur.user_id, ur.role_id
            </script>
            """)
    List<UserRoleCandidateRow> selectEnabledUserRoleCandidates(@Param("roleIds") List<Long> roleIds);
}
