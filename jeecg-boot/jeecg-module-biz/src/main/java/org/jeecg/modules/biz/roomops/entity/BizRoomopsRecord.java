package org.jeecg.modules.biz.roomops.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.util.Date;

@Data
@TableName("biz_roomops_record")
public class BizRoomopsRecord {
  @TableId(type = IdType.ASSIGN_ID)
  private String id;

  private String recordId;
  private String taskId;
  private Integer submissionNo;
  private String submissionType;
  private String reviewStatus;
  private Integer isCurrent;
  private String businessType;
  private String domainCode;
  private String domainShortCode;
  private String domainName;
  private String regionCode;
  private String regionName;
  private String roomId;
  private String roomName;
  private String inspectorName;
  private String dingtalkUserid;
  private String dingtalkUnionid;
  private BigDecimal latitude;
  private BigDecimal longitude;
  private BigDecimal accuracy;
  private BigDecimal temperature;
  private BigDecimal humidity;
  private Date capturedAt;
  private Date submittedAt;
  private String environmentStatus;
  private String deviceStatus;
  private String exceptionDesc;
  private String uploadMode;
  private String source;

  private String faultOrderNo;
  private String handlingResult;

  private String constructionContent;
  private String siteProblems;
  private String remainingIssues;
  private String remarkNote;

  private String rawFormJson;
  private String checkItemsJson;
  private String roomProof;
  private String evidenceStatus;
  private BigDecimal evidenceDistanceM;

  private String createBy;
  private Date createTime;
  private String updateBy;
  private Date updateTime;
  private String sysOrgCode;

  @TableField(exist = false)
  private Long photoCount;
}
