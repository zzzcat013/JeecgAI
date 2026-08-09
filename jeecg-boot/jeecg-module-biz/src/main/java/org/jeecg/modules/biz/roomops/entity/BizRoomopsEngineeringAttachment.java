package org.jeecg.modules.biz.roomops.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

@Data
@TableName("biz_roomops_engineering_attachment")
public class BizRoomopsEngineeringAttachment {
  @TableId(type = IdType.ASSIGN_ID)
  private String id;

  private String projectId;
  private String docType;
  private String originalFilename;
  private String storedFilename;
  private String storagePath;
  private String contentType;
  private String fileMd5;
  private Long fileSize;
  private String uploaderUserid;
  private String uploaderName;
  private Date uploadedAt;

  @TableField(exist = false)
  private Boolean duplicate;

  private String createBy;
  private Date createTime;
  private String updateBy;
  private Date updateTime;
  private String sysOrgCode;
}
