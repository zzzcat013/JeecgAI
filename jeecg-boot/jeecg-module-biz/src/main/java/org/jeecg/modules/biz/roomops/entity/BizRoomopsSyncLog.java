package org.jeecg.modules.biz.roomops.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

@Data
@TableName("biz_roomops_sync_log")
public class BizRoomopsSyncLog {
  @TableId(type = IdType.ASSIGN_ID)
  private String id;

  private String syncBatchId;
  private String sourceRecordId;
  private String recordId;
  private String businessType;
  private String syncDirection;
  private String syncStatus;
  private Integer retryCount;
  private String errorMessage;
  private Date startedAt;
  private Date finishedAt;

  private String createBy;
  private Date createTime;
  private String updateBy;
  private Date updateTime;
  private String sysOrgCode;
}
