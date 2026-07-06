package org.jeecg.modules.biz.ai5g.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.util.Date;

@Data
@TableName("biz_ai5g_docfile")
public class BizDocFile {
  @TableId(type = IdType.ASSIGN_ID)
  private String id;

  @TableField("actual_file_name")
  private String actualFileName;
  @TableField("original_name")
  private String originalName;
  @TableField("display_name")
  private String displayName;
  @TableField("version")
  private Integer version;
  @TableField("upload_time")
  private Date uploadTime;
  @TableField("file_type")
  private String fileType;

  @TableField("category_path")
  private String categoryPath;
  @TableField("file_year")
  private Integer fileYear;
  @TableField("remark")
  private String remark;

  @TableField("latest")
  private Boolean latest;
  @TableField("process_status")
  private String processStatus;

  @TableField("content_type")
  private String contentType;
  @TableField("size")
  private Long size;
  @TableField("storage_path")
  private String storagePath;
  @TableField("storage_filename")
  private String storageFilename;

  @TableField("md_converted")
  private Boolean mdConverted;
  @TableField("md_path")
  private String mdPath;

  @TableField("asset_root")
  private String assetRoot;
  @TableField("asset_manifest")
  private String assetManifest;
  @TableField("source_package_path")
  private String sourcePackagePath;

  @TableField("create_by")
  private String createBy;
  @TableField("create_time")
  private Date createTime;
  @TableField("update_by")
  private String updateBy;
  @TableField("update_time")
  private Date updateTime;
}
