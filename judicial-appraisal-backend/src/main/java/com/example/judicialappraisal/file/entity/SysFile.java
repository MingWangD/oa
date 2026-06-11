package com.example.judicialappraisal.file.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("sys_file")
public class SysFile {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String originalName;
    private String fileName;
    private String fileExt;
    private String contentType;
    private Long fileSize;
    private String storageBucket;
    private String storageKey;
    private String md5;
    private Long uploadUserId;
    private String uploadUserName;
    private LocalDateTime createdTime;
    private Integer deleted;
}
