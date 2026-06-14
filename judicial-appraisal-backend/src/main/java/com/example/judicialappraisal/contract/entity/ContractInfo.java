package com.example.judicialappraisal.contract.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("contract_info")
public class ContractInfo {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String contractNo;
    private String contractName;
    private String customerName;
    private Long relatedCaseId;
    private BigDecimal amount;
    private Long ownerId;
    private String ownerName;
    private Long departmentId;
    private String departmentName;
    private String status;
    private Long archiveDocumentId;
    private String reviewOpinion;
    private LocalDateTime submittedAt;
    private LocalDateTime approvedAt;
    private LocalDateTime archivedAt;
    private Long createdBy;
    private LocalDateTime createdAt;
    private Long updatedBy;
    private LocalDateTime updatedAt;
    private Integer deleted;
}
