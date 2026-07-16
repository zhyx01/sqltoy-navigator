plugins {
    id("java")
    id("org.jetbrains.intellij.platform")
}

group = "com.ax.sqltoy"
version = "1.0.9"

dependencies {
    intellijPlatform {
        intellijIdea("2024.1.1")
        bundledPlugin("com.intellij.java")
    }
}

intellijPlatform {
    pluginConfiguration {
        ideaVersion {
            sinceBuild = "241.15989.150"
        }

        changeNotes = """
            Change version :
                1. Added SQL navigation for multi data sources(oracle/vastbase/gaussdb/postgresql).
                2. Optimized tooltip.
                
                
                1. 添加多数据源 SQL 跳转（oracle/vastbase/gaussdb/postgresql）
                2. Tooltip 优化
        """.trimIndent()
    }
}

tasks {
    withType<JavaCompile> {
        sourceCompatibility = "17"
        targetCompatibility = "17"
        options.encoding = "UTF-8"
    }
}
