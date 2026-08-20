package org.jeecg.modules.biz.ai5g.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import lombok.extern.slf4j.Slf4j;
import org.jeecg.modules.biz.ai5g.entity.BizDocFile;
import org.jeecg.modules.biz.ai5g.service.IBizDocFileService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class BizDocFileStartupRecovery {

  @Autowired
  private IBizDocFileService bizDocFileService;

  @EventListener(ApplicationReadyEvent.class)
  public void recoverInterruptedConversions() {
    try {
      long count = bizDocFileService.count(new LambdaQueryWrapper<BizDocFile>().eq(BizDocFile::getProcessStatus, "processing"));
      if (count == 0) {
        return;
      }
      long asyncCount = bizDocFileService.count(new LambdaQueryWrapper<BizDocFile>()
          .eq(BizDocFile::getProcessStatus, "processing")
          .isNotNull(BizDocFile::getMineruTaskId));
      long interruptedCount = count - asyncCount;
      if (interruptedCount > 0) {
        bizDocFileService.update(new LambdaUpdateWrapper<BizDocFile>()
            .eq(BizDocFile::getProcessStatus, "processing")
            .isNull(BizDocFile::getMineruTaskId)
            .set(BizDocFile::getProcessStatus, "failed")
            .set(BizDocFile::getRemark, "服务重启导致转换中断，请重新提交"));
      }
      log.info("AI5G 文档转换启动恢复完成，重置中断文档: {}, 保留MinerU异步任务: {}", interruptedCount, asyncCount);
    } catch (Exception e) {
      log.warn("AI5G 文档转换启动恢复失败", e);
    }
  }
}
