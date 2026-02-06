package org.jeecg.modules.airag.llm.handler;

import java.io.File;

/**
 * 文档解析策略接口
 */
public interface IDocumentParser {
    /**
     * 是否支持该文件类型
     * @param fileType 文件后缀 (如 pdf, docx)
     * @return true if supported
     */
    boolean support(String fileType);

    /**
     * 解析文档
     * @param file 源文件
     * @param config 配置信息 (如远程URL等)
     * @return 解析后的 Markdown 内容，如果解析失败返回 null
     */
    String parse(File file, Object config);
}
