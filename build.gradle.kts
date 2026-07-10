plugins {
    id("java")
    id("org.jetbrains.intellij.platform")
}

group = "com.ax.sqltoy"
version = "1.0.8"

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
                1. Add navigation for sqlId references in XML files.
                
                
                1. 添加 xml 文件中引用 sqlId 的跳转
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
