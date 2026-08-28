package org.jeecg.modules.biz.roomops.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;
import java.util.List;

@Data
@TableName("biz_roomops_task")
public class BizRoomopsTask {
  @TableId(type = IdType.ASSIGN_ID)
  private String id;

  private String taskId;
  private String businessType;
  private String taskTitle;
  private String taskContent;

  private String domainCode;
  private String domainShortCode;
  private String domainName;
  private String regionCode;
  private String regionName;
  private String roomId;
  private String roomName;

  private String assignerUserid;
  private String assignerName;
  private String assigneeUserid;
  private String assigneeName;
  private String candidateUserids;
  private String candidateNames;

  private String status;
  private String priority;
  private Integer roundCount;

  private Date deadlineAt;
  private Date assignedAt;
  private Date claimedAt;
  private Date submittedAt;
  private Date confirmedAt;

  private String rejectRemark;
  private String confirmRemark;
  private String confirmBy;
  private String confirmUserid;

  private String recordId;
  private String projectId;

  private Integer archived;
  private Date archivedAt;
  private String archivedBy;

  private String createBy;
  private Date createTime;
  private String updateBy;
  private Date updateTime;
  private String sysOrgCode;

  @TableField(exist = false)
  private List<BizRoomopsTaskRound> rounds;

  @TableField(exist = false)
  private Boolean warning;
}
