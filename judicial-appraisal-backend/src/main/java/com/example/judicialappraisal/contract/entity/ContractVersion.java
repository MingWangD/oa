package com.example.judicialappraisal.contract.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("contract_version")
public class ContractVersion {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long contractId;
    private Integer versionNo;
    private String title;
    private String content;
    private String changeNote;
    private Long createdBy;
    private LocalDateTime createdAt;
}
