package org.jeecg.modules.biz.ai5g.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;
import org.jeecg.modules.biz.ai5g.entity.BizDocFile;
import org.jeecg.modules.biz.ai5g.mapper.BizDocFileMapper;
import org.jeecg.modules.biz.ai5g.service.IBizDocFileService;

@Service
public class BizDocFileServiceImpl extends ServiceImpl<BizDocFileMapper, BizDocFile> implements IBizDocFileService {}

