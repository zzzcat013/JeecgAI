package org.jeecg.modules.biz.roomops.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.jeecg.modules.biz.roomops.entity.BizRoomopsPhoto;
import org.jeecg.modules.biz.roomops.mapper.BizRoomopsPhotoMapper;
import org.jeecg.modules.biz.roomops.service.IBizRoomopsPhotoService;
import org.springframework.stereotype.Service;

@Service
public class BizRoomopsPhotoServiceImpl extends ServiceImpl<BizRoomopsPhotoMapper, BizRoomopsPhoto> implements IBizRoomopsPhotoService {}
