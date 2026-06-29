plugins {
    id("java")
    id("org.jetbrains.intellij.platform")
}

group = "com.ax.sqltoy"
version = "1.0.6"

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
                1. Check whether there are duplicate sqlId in the current XML.
                
                1. 检验当前 xml 中是否存在重复定义的 sqlId
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
