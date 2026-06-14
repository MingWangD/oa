package com.example.judicialappraisal.workflow.design;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("form_definition")
public class FormDefinition {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String formCode;
    private String formName;
    private String category;
    private Integer currentPublishedVersion;
    private Integer enabled;
    private Long createdBy;
    private LocalDateTime createdTime;
    private Long updatedBy;
    private LocalDateTime updatedTime;
    private Integer deleted;
}
