package com.example.judicialappraisal.workflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.judicialappraisal.workflow.entity.WfTransitionDef;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface WfTransitionDefMapper extends BaseMapper<WfTransitionDef> {
    @Delete("DELETE FROM wf_transition_def WHERE wf_id = #{wfId}")
    int physicalDeleteByWfId(@Param("wfId") Long wfId);
}
