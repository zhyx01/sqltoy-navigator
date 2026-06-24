package com.ax.sqltoy;

import com.intellij.openapi.util.IconLoader;

import javax.swing.Icon;

/**
 * 集中管理插件图标加载。
 *
 * @author ax
 * @date 2026-05-30
 */
final class SqlToyIcons {

    /**
     * 用于 SQL 导航标记和 SqlToy SQL 文件类型的边栏图标。
     */
    static final Icon SQL_MARKER = IconLoader.getIcon("/icons/sqlToyMarker.svg", SqlToyIcons.class);

    static final Icon JAVA_MARKER = IconLoader.getIcon("/icons/javaMarker.svg", SqlToyIcons.class);

    /**
     * 工具类，不需要创建实例。
     */
    private SqlToyIcons() {
    }
}
