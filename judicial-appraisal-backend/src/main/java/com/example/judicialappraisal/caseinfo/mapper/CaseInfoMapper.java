package com.example.judicialappraisal.caseinfo.mapper;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.example.judicialappraisal.caseinfo.entity.CaseInfo;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Result;
import org.apache.ibatis.annotations.Results;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface CaseInfoMapper extends BaseMapper<CaseInfo> {

    @InterceptorIgnore(dataPermission = "true")
    @Select("SELECT * FROM case_info WHERE id = #{caseId} LIMIT 1")
    @Results({
            @Result(column = "form_data", property = "formData", typeHandler = JacksonTypeHandler.class)
    })
    CaseInfo selectRawById(@Param("caseId") Long caseId);
}
