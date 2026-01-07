package org.jeecg.modules.biz.ai5g.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("biz_ai5g_doctype")
public class BizDocType {
  @TableId(type = IdType.ASSIGN_ID)
  private String id;

  private Integer level;
  private String code;
  private String name;
  private String parentCode;
  private Integer status;
}
