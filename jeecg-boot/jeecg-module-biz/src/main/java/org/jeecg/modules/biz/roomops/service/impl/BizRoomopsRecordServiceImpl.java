package org.jeecg.modules.biz.roomops.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.jeecg.modules.biz.roomops.entity.BizRoomopsRecord;
import org.jeecg.modules.biz.roomops.mapper.BizRoomopsRecordMapper;
import org.jeecg.modules.biz.roomops.service.IBizRoomopsRecordService;
import org.springframework.stereotype.Service;

@Service
public class BizRoomopsRecordServiceImpl extends ServiceImpl<BizRoomopsRecordMapper, BizRoomopsRecord> implements IBizRoomopsRecordService {}
