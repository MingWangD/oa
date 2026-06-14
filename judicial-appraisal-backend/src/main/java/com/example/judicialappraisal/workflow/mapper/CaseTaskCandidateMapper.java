package com.example.judicialappraisal.workflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.judicialappraisal.workflow.entity.CaseTaskCandidate;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface CaseTaskCandidateMapper extends BaseMapper<CaseTaskCandidate> {

    @Select("""
            <script>
            SELECT DISTINCT task_id
            FROM case_task_candidate
            WHERE candidate_user_id = #{userId}
            <if test="roleIds != null and roleIds.size() > 0">
              OR candidate_role_id IN
              <foreach collection="roleIds" item="roleId" open="(" separator="," close=")">
                #{roleId}
              </foreach>
            </if>
            ORDER BY task_id DESC
            </script>
            """)
    List<Long> selectEligibleTaskIds(@Param("userId") Long userId, @Param("roleIds") List<Long> roleIds);
}
