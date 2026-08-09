package org.jeecg.modules.biz.roomops.entity;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;
import java.util.List;

@Data
@TableName("biz_roomops_engineering_project")
public class BizRoomopsEngineeringProject {
  @TableId(type = IdType.ASSIGN_ID)
  private String id;

  private String projectId;
  private String projectName;
  private String category;
  private String ownership;
  private String domainCode;
  private String domainShortCode;
  private String domainName;
  private String regionCode;
  private String regionName;
  private String roomId;
  private String roomName;
  private String status;

  @JsonFormat(pattern = "yyyy-MM-dd", timezone = "GMT+8")
  private Date startReportDate;
  private String startReportCompany;
  private String startReportPerson;
  private String startReportContent;
  private String description;

  private Integer archived;
  private Date archivedAt;
  private String archivedBy;

  private String createBy;
  private Date createTime;
  private String updateBy;
  private Date updateTime;
  private String sysOrgCode;

  @TableField(exist = false)
  private Integer attachmentCount;

  @TableField(exist = false)
  private Integer taskCount;

  @TableField(exist = false)
  private List<BizRoomopsEngineeringAttachment> attachments;
}
