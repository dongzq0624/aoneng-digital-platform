package com.aoneng.rag.common.constant;

/**
 * 上传文件相关常量。
 *
 * <p>所有允许上传的文件扩展名（{@link #ALLOWED_EXTENSIONS}）必须在以下三个地方保持同步：
 * <ul>
 *   <li>本类 {@link #ALLOWED_EXTENSIONS}</li>
 *   <li>{@code DocParseService.EXPECTED_MEDIA_TYPES}</li>
 *   <li>{@code KnowledgeBaseService.ALLOWED_EXTENSIONS}</li>
 * </ul>
 * 任何一处变更都需要联动更新其他位置，避免不一致。</p>
 */
public final class FileConstants {

    /** 支持上传的文件扩展名（小写，不含点号）。 */
    public static final java.util.Set<String> ALLOWED_EXTENSIONS =
            java.util.Set.of("pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx",
                    "rtf", "wp", "jpg", "jpeg", "png", "md", "txt");

    /** 单个文件最大字节数：50MB。 */
    public static final long MAX_FILE_BYTES = 50L * 1024 * 1024;

    private FileConstants() {
    }
}
