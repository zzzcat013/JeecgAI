package org.jeecg.modules.biz.roomops.service;

import com.baomidou.mybatisplus.extension.service.IService;
import org.jeecg.modules.biz.roomops.entity.BizRoomopsTask;
import org.jeecg.modules.biz.roomops.entity.BizRoomopsTaskRound;

import java.util.List;

public interface IBizRoomopsTaskService extends IService<BizRoomopsTask> {
  BizRoomopsTask createTask(BizRoomopsTask task, String operatorUserid, String operatorName);

  BizRoomopsTask updateTask(BizRoomopsTask task, String operatorUserid, String operatorName);

  void confirmTask(String taskId, String remark, String operatorUserid, String operatorName);

  void rejectTask(String taskId, String remark, String reassignUserid, String reassignName,
                  boolean clearAssignee, String operatorUserid, String operatorName);

  void archiveTask(String taskId, boolean archived, String operatorUserid, String operatorName);
  void deleteTasks(List<String> ids);


  void pushTask(String taskId) throws Exception;

  void pushAllActive() throws Exception;

  void pullTaskUpdatesFromVps();

  void markSubmitted(String recordId, String taskId, String submissionType,
                     String inspectorName, String inspectorUserid);

  List<BizRoomopsTaskRound> listRounds(String taskId);
}
