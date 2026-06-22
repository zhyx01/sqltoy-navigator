plugins {
    id("java")
    id("org.jetbrains.intellij.platform")
}

group = "com.ax.sqltoy"
version = "1.0.5"

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
                1. Add syntax highlighting for INSERT statements.
                2. Add constant definition for SQL jump identifier.
                3. Change icon.
                
                1. 添加 INSERT 语句的语法高亮
                2. 添加常量定义 SQL 跳转标识
                3. 变更图标
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
