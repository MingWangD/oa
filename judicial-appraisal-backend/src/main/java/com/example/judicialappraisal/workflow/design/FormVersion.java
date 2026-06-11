package com.example.judicialappraisal.workflow.design;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("form_version")
public class FormVersion {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long formId;
    private String formCode;
    private String formName;
    private Integer versionNo;
    private String status;
    private String inputFilesJson;
    private String outputFilesJson;
    private String versionedArtifactsJson;
    private String fieldSchemaJson;
    private String layoutSchemaJson;
    private String validationSchemaJson;
    private String permissionSchemaJson;
    private String linkageSchemaJson;
    private String calculationSchemaJson;
    private String attachmentSchemaJson;
    private String subtableSchemaJson;
    private String notesJson;
    private Long sourceVersionId;
    private Long publishedBy;
    private LocalDateTime publishedTime;
    private Integer immutableFlag;
    private Long createdBy;
    private LocalDateTime createdTime;
    private Long updatedBy;
    private LocalDateTime updatedTime;
    private Integer deleted;
}
