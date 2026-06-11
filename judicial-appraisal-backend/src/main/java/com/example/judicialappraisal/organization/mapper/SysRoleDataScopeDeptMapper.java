package com.example.judicialappraisal.organization.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.judicialappraisal.organization.entity.SysRoleDataScopeDept;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import java.util.List;

@Mapper
public interface SysRoleDataScopeDeptMapper extends BaseMapper<SysRoleDataScopeDept> {

    @Delete("DELETE FROM sys_role_data_scope_dept WHERE role_id = #{roleId}")
    int deleteByRoleId(@Param("roleId") Long roleId);

    @Insert("INSERT INTO sys_role_data_scope_dept(role_id, dept_id) VALUES(#{roleId}, #{deptId})")
    int insertRoleDept(@Param("roleId") Long roleId, @Param("deptId") Long deptId);

    @Select("SELECT dept_id FROM sys_role_data_scope_dept WHERE role_id = #{roleId} ORDER BY dept_id")
    List<Long> selectDeptIdsByRoleId(@Param("roleId") Long roleId);
}
