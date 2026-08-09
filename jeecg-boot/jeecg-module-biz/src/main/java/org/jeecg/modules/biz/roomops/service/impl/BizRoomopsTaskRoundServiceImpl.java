package org.jeecg.modules.biz.roomops.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.jeecg.modules.biz.roomops.entity.BizRoomopsTaskRound;
import org.jeecg.modules.biz.roomops.mapper.BizRoomopsTaskRoundMapper;
import org.jeecg.modules.biz.roomops.service.IBizRoomopsTaskRoundService;
import org.springframework.stereotype.Service;

@Service
public class BizRoomopsTaskRoundServiceImpl extends ServiceImpl<BizRoomopsTaskRoundMapper, BizRoomopsTaskRound>
    implements IBizRoomopsTaskRoundService {}
