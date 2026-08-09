package org.jeecg.modules.biz.roomops.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.jeecg.modules.biz.roomops.entity.BizRoomopsSyncLog;
import org.jeecg.modules.biz.roomops.mapper.BizRoomopsSyncLogMapper;
import org.jeecg.modules.biz.roomops.service.IBizRoomopsSyncLogService;
import org.springframework.stereotype.Service;

@Service
public class BizRoomopsSyncLogServiceImpl extends ServiceImpl<BizRoomopsSyncLogMapper, BizRoomopsSyncLog> implements IBizRoomopsSyncLogService {}
