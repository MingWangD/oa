package com.example.judicialappraisal.contract.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.judicialappraisal.contract.entity.ContractAttachment;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface ContractAttachmentMapper extends BaseMapper<ContractAttachment> {
    @Select("SELECT * FROM contract_attachment WHERE contract_id = #{contractId} AND file_id = #{fileId} LIMIT 1")
    ContractAttachment selectWithDeleted(@Param("contractId") Long contractId, @Param("fileId") Long fileId);
}
