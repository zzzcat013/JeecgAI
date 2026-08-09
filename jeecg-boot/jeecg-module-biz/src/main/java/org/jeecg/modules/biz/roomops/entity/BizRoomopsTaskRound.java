package org.jeecg.modules.biz.roomops.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

@Data
@TableName("biz_roomops_task_round")
public class BizRoomopsTaskRound {
  @TableId(type = IdType.ASSIGN_ID)
  private String id;

  private String taskId;
  private Integer roundNo;
  private String action;
  private String fromStatus;
  private String toStatus;
  private String operatorUserid;
  private String operatorName;
  private String remark;
  private Date actionTime;
  private String createBy;
  private Date createTime;
}
