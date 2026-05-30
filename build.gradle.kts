plugins {
    id("java")
    id("org.jetbrains.intellij.platform")
}

group = "com.ax.sqltoy"
version = "0.1.1"

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
            and highlight SQL syntax inside SqlToy XML SQL tags with a bundled lightweight SQL highlighter.
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
