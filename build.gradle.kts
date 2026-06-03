plugins {
    id("java")
    id("org.jetbrains.intellij.platform")
}

group = "com.ax.sqltoy"
version = "1.0.1"

dependencies {
    intellijPlatform {
        intellijIdea("2025.2.6.1")
        bundledPlugin("com.intellij.java")
    }
}

intellijPlatform {
    pluginConfiguration {
        ideaVersion {
            sinceBuild = "252"
        }

        changeNotes = """
            Initial version: navigate Java SqlToy sqlId string literals to XML &lt;sql id="..."&gt; definitions
            and inject IDEA SQL support inside SqlToy XML SQL tags when available.
        """.trimIndent()
    }
}

tasks {
    withType<JavaCompile> {
        sourceCompatibility = "21"
        targetCompatibility = "21"
        options.encoding = "UTF-8"
    }
}
