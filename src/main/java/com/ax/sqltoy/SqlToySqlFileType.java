package com.ax.sqltoy;

import com.intellij.openapi.fileTypes.LanguageFileType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.Icon;

/**
 * 嵌入式 SqlToy SQL 语言的文件类型门面。
 *
 * @author ax
 * @date 2026-05-30
 */
final class SqlToySqlFileType extends LanguageFileType {

    /**
     * 供解析器定义使用的文件类型单例。
     */
    static final SqlToySqlFileType INSTANCE = new SqlToySqlFileType();

    /**
     * 将此文件类型绑定到 SqlToy SQL 语言。
     */
    private SqlToySqlFileType() {
        super(SqlToySqlLanguage.INSTANCE);
    }

    /**
     * 返回文件类型名称。
     *
     * @return 文件类型名称
     */
    @Override
    public @NotNull String getName() {
        return "SqlToy SQL";
    }

    /**
     * 返回用于界面展示的简短描述。
     *
     * @return 文件类型描述
     */
    @Override
    public @NotNull String getDescription() {
        return "SqlToy embedded SQL";
    }

    /**
     * 返回嵌入式 SQL 片段的合成扩展名。
     *
     * @return 默认扩展名
     */
    @Override
    public @NotNull String getDefaultExtension() {
        return "sqltoy-sql";
    }

    /**
     * 返回 SqlToy SQL 片段使用的图标。
     *
     * @return SQL 标记图标
     */
    @Override
    public @Nullable Icon getIcon() {
        return SqlToyIcons.SQL_MARKER;
    }
}
