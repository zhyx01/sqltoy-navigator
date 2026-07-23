plugins {
    id("java")
    id("org.jetbrains.intellij.platform")
}

group = "com.ax.sqltoy"
version = "1.1.0"

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
                1. The sqlId referenced in the Java code does not exist, it is grayed out.\n
                
                
                1. Java 代码中引用的 sqlId 不存在，置灰
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
