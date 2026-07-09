package com.ax.sqltoy;

import com.intellij.openapi.util.TextRange;
import org.jetbrains.annotations.NotNull;

/**
 * SqlToy SQL 片段中的 include 引用信息。
 *
 * @param sqlId               include 指向的 sqlId
 * @param sqlIdRangeInElement sqlId 在 XML 文本元素内的相对范围
 */
record SqlToySqlInclude(
        @NotNull String sqlId,
        @NotNull TextRange sqlIdRangeInElement
) {
}
