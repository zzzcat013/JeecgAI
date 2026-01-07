package org.jeecg.modules.biz.ai5g.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.TableField;
import lombok.Data;
import java.util.Date;

@Data
@TableName("biz_document")
public class BizDocument {
  @TableId(type = IdType.ASSIGN_ID)
  private String id;

  private String title;
  private String originalName;
  private String ext;
  private String contentType;
  private Long size;

  private String directoryName;
  private String typeCode1;
  private String typeCode2;
  private String typeCode3;

  private String storagePath;
  private String storageFilename;

  private Boolean mdConverted;
  private String mdPath;

  private String createBy;
  private Date createTime;
  private String updateBy;
  private Date updateTime;
}

