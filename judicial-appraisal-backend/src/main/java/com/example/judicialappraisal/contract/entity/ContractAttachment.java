package com.example.judicialappraisal.contract.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("contract_attachment")
public class ContractAttachment {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long contractId;
    private Long fileId;
    private String fileName;
    private String artifactCode;
    private Long createdBy;
    private LocalDateTime createdAt;
    private Integer deleted;
}
