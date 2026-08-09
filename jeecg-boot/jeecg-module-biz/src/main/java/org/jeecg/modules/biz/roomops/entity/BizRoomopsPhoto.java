package org.jeecg.modules.biz.roomops.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.util.Date;

@Data
@TableName("biz_roomops_photo")
public class BizRoomopsPhoto {
  @TableId(type = IdType.ASSIGN_ID)
  private String id;

  private String recordId;
  private Integer photoIndex;
  private Integer photoTotal;
  private String originalFilename;
  private String storedFilename;
  private String storagePath;
  private String contentType;
  private Long fileSize;
  private Date photoCapturedAt;
  private BigDecimal photoLatitude;
  private BigDecimal photoLongitude;
  private BigDecimal photoAccuracy;
  private String photoRemark;
  private Integer watermarked;
  private Date uploadedAt;

  private String createBy;
  private Date createTime;
  private String updateBy;
  private Date updateTime;
  private String sysOrgCode;
}
