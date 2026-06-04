package com.ax.sqltoy;

import com.intellij.lang.Language;
import org.jetbrains.annotations.NotNull;

/**
 * 定义用于 SqlToy XML 标签内的轻量级嵌入式 SQL 语言。
 *
 * @author ax
 * @date 2026-05-30
 */
public final class SqlToySqlLanguage extends Language {

    /**
     * 在 plugin.xml 中注册的语言单例。
     */
    public static final SqlToySqlLanguage INSTANCE = new SqlToySqlLanguage();

    /**
     * 使用 IntelliJ 扩展点所需的稳定 ID 创建语言。
     */
    private SqlToySqlLanguage() {
        super("SqlToySQL");
    }

    /**
     * 返回 IntelliJ 显示的人类可读语言名称。
     *
     * @return 嵌入式 SQL 语言的显示名称
     */
    @Override
    public @NotNull String getDisplayName() {
        return "SqlToy SQL";
    }
}
