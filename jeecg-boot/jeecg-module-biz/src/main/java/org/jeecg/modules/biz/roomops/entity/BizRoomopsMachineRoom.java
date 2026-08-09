package org.jeecg.modules.biz.roomops.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

@Data
@TableName("biz_roomops_machine_room")
public class BizRoomopsMachineRoom {
  @TableId(type = IdType.ASSIGN_ID)
  private String id;

  private String roomId;
  private String roomName;
  private String domainCode;
  private String domainShortCode;
  private String domainName;
  private String regionCode;
  private String regionName;
  private String remark;
  private String status;

  private String createBy;
  private Date createTime;
  private String updateBy;
  private Date updateTime;
  private String sysOrgCode;
}
