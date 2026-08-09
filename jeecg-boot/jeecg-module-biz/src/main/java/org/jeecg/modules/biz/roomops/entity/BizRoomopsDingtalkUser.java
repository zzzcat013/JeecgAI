package org.jeecg.modules.biz.roomops.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

@Data
@TableName("biz_roomops_dingtalk_user")
public class BizRoomopsDingtalkUser {
  @TableId(type = IdType.ASSIGN_ID)
  private String id;

  private String dingtalkUserid;
  private String dingtalkUnionid;
  private String name;
  private String mobile;
  private String avatar;
  private String deptId;
  private String deptName;
  private String defaultDomainCode;
  private String defaultDomainShortCode;
  private String defaultDomainName;
  private String defaultRegionCode;
  private String defaultRegionName;
  private String active;
  private Integer dingtalkSynced;
  private Date lastLoginTime;
  private Date lastSyncTime;

  private String createBy;
  private Date createTime;
  private String updateBy;
  private Date updateTime;
  private String sysOrgCode;
}
