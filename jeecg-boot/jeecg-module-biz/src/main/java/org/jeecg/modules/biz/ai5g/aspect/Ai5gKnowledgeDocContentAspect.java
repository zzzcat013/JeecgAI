package org.jeecg.modules.biz.ai5g.aspect;

import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.jeecg.common.util.oConvertUtils;
import org.jeecg.modules.airag.llm.entity.AiragKnowledgeDoc;
import org.springframework.stereotype.Component;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Normalize ai5g document asset URLs before AIRag knowledge documents are saved.
 */
@Slf4j
@Aspect
@Component
public class Ai5gKnowledgeDocContentAspect {

    private static final String DOMAIN_URL_PLACEHOLDER = "#{domainURL}";
    private static final Pattern AI5G_ASSET_ABSOLUTE_URL = Pattern.compile(
            "https?://[^\\s)'\"]+?(/ai5g/doc/assets/[^\\s)'\"]+)",
            Pattern.CASE_INSENSITIVE);

    @Around("execution(* org.jeecg.modules.airag.llm.service.impl.AiragKnowledgeDocServiceImpl.editDocument(..))")
    public Object normalizeAi5gAssetUrls(ProceedingJoinPoint point) throws Throwable {
        Object[] args = point.getArgs();
        if (args != null && args.length > 0 && args[0] instanceof AiragKnowledgeDoc) {
            AiragKnowledgeDoc doc = (AiragKnowledgeDoc) args[0];
            String content = doc.getContent();
            String normalized = normalizeContent(content);
            if (!java.util.Objects.equals(content, normalized)) {
                doc.setContent(normalized);
                log.info("AI5G knowledge document asset urls normalized, docId={}, title={}", doc.getId(), doc.getTitle());
            }
        }
        return point.proceed(args);
    }

    private String normalizeContent(String content) {
        if (oConvertUtils.isEmpty(content) || !content.contains("/ai5g/doc/assets/")) {
            return content;
        }
        Matcher matcher = AI5G_ASSET_ABSOLUTE_URL.matcher(content);
        StringBuffer sb = new StringBuffer();
        while (matcher.find()) {
            String assetPath = matcher.group(1);
            matcher.appendReplacement(sb, Matcher.quoteReplacement(DOMAIN_URL_PLACEHOLDER + assetPath));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }
}
