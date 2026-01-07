package org.jeecg.modules.biz.ai5g.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;
import org.jeecg.modules.biz.ai5g.entity.BizDocType;
import org.jeecg.modules.biz.ai5g.mapper.BizDocTypeMapper;
import org.jeecg.modules.biz.ai5g.service.IBizDocTypeService;

@Service
public class BizDocTypeServiceImpl extends ServiceImpl<BizDocTypeMapper, BizDocType> implements IBizDocTypeService {}

