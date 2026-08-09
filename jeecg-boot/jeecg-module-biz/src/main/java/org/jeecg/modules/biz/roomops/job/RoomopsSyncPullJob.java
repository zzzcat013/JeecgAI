package org.jeecg.modules.biz.roomops.job;

import lombok.extern.slf4j.Slf4j;
import org.jeecg.common.api.vo.Result;
import org.jeecg.common.util.DateUtils;
import org.jeecg.modules.biz.roomops.controller.BizRoomopsSyncPullController;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * 机房运维 VPS 数据主动拉取任务。
 */
@Slf4j
public class RoomopsSyncPullJob implements Job {

  /**
   * Quartz 参数。可填每次拉取数量，例如：5、20。
   */
  private String parameter;

  @Autowired
  private BizRoomopsSyncPullController syncPullController;

  public void setParameter(String parameter) {
    this.parameter = parameter;
  }

  @Override
  public void execute(JobExecutionContext jobExecutionContext) throws JobExecutionException {
    int limit = parseLimit(parameter);
    log.info("RoomopsSyncPullJob start, limit={}, time={}", limit, DateUtils.now());
    try {
      Result<BizRoomopsSyncPullController.PullResult> result = syncPullController.pullFromVps(limit);
      if (Boolean.TRUE.equals(result.isSuccess())) {
        BizRoomopsSyncPullController.PullResult data = result.getResult();
        log.info("RoomopsSyncPullJob success, batchId={}, pulled={}, succeeded={}, failed={}",
            data == null ? "" : data.getSyncBatchId(),
            data == null ? 0 : data.getPulled(),
            data == null ? 0 : data.getSucceeded(),
            data == null ? 0 : data.getFailed());
      } else {
        log.warn("RoomopsSyncPullJob failed, message={}", result.getMessage());
      }
    } catch (Exception e) {
      log.error("RoomopsSyncPullJob error", e);
      throw new JobExecutionException(e);
    }
  }

  private int parseLimit(String value) {
    if (value == null || value.trim().isEmpty()) {
      return 20;
    }
    try {
      int limit = Integer.parseInt(value.trim());
      return Math.min(Math.max(limit, 1), 100);
    } catch (Exception e) {
      log.warn("RoomopsSyncPullJob parameter is not a number, use default 20, parameter={}", value);
      return 20;
    }
  }
}
