package com.example.judicialappraisal.caseinfo.mapper;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.judicialappraisal.caseinfo.entity.CaseInfo;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface CaseInfoMapper extends BaseMapper<CaseInfo> {

    @InterceptorIgnore(dataPermission = "true")
    @Select("SELECT * FROM case_info WHERE id = #{caseId} LIMIT 1")
    CaseInfo selectRawById(@Param("caseId") Long caseId);
}
