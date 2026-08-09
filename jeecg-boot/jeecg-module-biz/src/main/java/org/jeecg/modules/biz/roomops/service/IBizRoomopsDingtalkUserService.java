package org.jeecg.modules.biz.roomops.service;

import com.baomidou.mybatisplus.extension.service.IService;
import org.jeecg.modules.biz.roomops.entity.BizRoomopsDingtalkUser;

public interface IBizRoomopsDingtalkUserService extends IService<BizRoomopsDingtalkUser> {
  DingtalkUserSyncResult syncFromDingtalk();

  class DingtalkUserSyncResult {
    private int departmentCount;
    private int fetchedUserCount;
    private int createdCount;
    private int updatedCount;
    private int skippedCount;

    public int getDepartmentCount() {
      return departmentCount;
    }

    public void setDepartmentCount(int departmentCount) {
      this.departmentCount = departmentCount;
    }

    public int getFetchedUserCount() {
      return fetchedUserCount;
    }

    public void setFetchedUserCount(int fetchedUserCount) {
      this.fetchedUserCount = fetchedUserCount;
    }

    public int getCreatedCount() {
      return createdCount;
    }

    public void setCreatedCount(int createdCount) {
      this.createdCount = createdCount;
    }

    public int getUpdatedCount() {
      return updatedCount;
    }

    public void setUpdatedCount(int updatedCount) {
      this.updatedCount = updatedCount;
    }

    public int getSkippedCount() {
      return skippedCount;
    }

    public void setSkippedCount(int skippedCount) {
      this.skippedCount = skippedCount;
    }
  }
}
