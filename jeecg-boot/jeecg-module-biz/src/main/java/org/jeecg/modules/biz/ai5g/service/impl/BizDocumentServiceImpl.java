package org.jeecg.modules.biz.ai5g.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;
import org.jeecg.modules.biz.ai5g.entity.BizDocument;
import org.jeecg.modules.biz.ai5g.mapper.BizDocumentMapper;
import org.jeecg.modules.biz.ai5g.service.IBizDocumentService;

@Service
public class BizDocumentServiceImpl extends ServiceImpl<BizDocumentMapper, BizDocument> implements IBizDocumentService {}

