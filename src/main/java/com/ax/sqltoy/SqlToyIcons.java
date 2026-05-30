package com.ax.sqltoy;

import com.intellij.openapi.util.IconLoader;

import javax.swing.Icon;

/**
 * Centralizes plugin icon loading.
 *
 * @author ax
 * @date 2026-05-30
 */
final class SqlToyIcons {

    /**
     * Gutter icon used for SQL navigation markers and SqlToy SQL file type.
     */
    static final Icon SQL_MARKER = IconLoader.getIcon("/icons/sqlToyMarker.svg", SqlToyIcons.class);

    /**
     * Utility class; instances are not needed.
     */
    private SqlToyIcons() {
    }
}
