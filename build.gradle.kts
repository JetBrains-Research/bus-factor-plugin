import org.jetbrains.changelog.Changelog
import org.jetbrains.changelog.markdownToHTML
import org.jetbrains.intellij.tasks.RunIdeTask
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

fun properties(key: String) = providers.gradleProperty(key)
fun environment(key: String) = providers.environmentVariable(key)

plugins {
  id("java") // Java support
  alias(libs.plugins.kotlin) // Kotlin support
  alias(libs.plugins.gradleIntelliJPlugin) // Gradle IntelliJ Plugin
  alias(libs.plugins.changelog) // Gradle Changelog Plugin
  alias(libs.plugins.qodana) // Gradle Qodana Plugin
  alias(libs.plugins.kover) // Gradle Kover Plugin
  kotlin("plugin.serialization") version "1.8.0"
}

group = properties("pluginGroup").get()
version = properties("pluginVersion").get()

// Configure project's dependencies
repositories {
  mavenCentral()
  maven {
    url = uri("https://packages.jetbrains.team/maven/p/ictl-public/public-maven")
  }
}

// Dependencies are managed with Gradle version catalog - read more: https://docs.gradle.org/current/userguide/platforms.html#sub:version-catalog
dependencies {
  implementation("org.jetbrains.research.ictl:bf-core:0.0.13")
  implementation(libs.annotations)
}

// Set the JVM language level used to build the project. Use Java 11 for 2020.3+, and Java 17 for 2022.2+.
kotlin {
  jvmToolchain(17)
}

// Configure Gradle IntelliJ Plugin - read more: https://plugins.jetbrains.com/docs/intellij/tools-gradle-intellij-plugin.html
intellij {
  pluginName = properties("pluginName")
  version = properties("platformVersion")
  type = properties("platformType")

  // Plugin Dependencies. Uses `platformPlugins` property from the gradle.properties file.
  plugins = properties("platformPlugins").map { it.split(',').map(String::trim).filter(String::isNotEmpty) }
}

// Configure Gradle Changelog Plugin - read more: https://github.com/JetBrains/gradle-changelog-plugin
//changelog {
//    groups.empty()
//    repositoryUrl = properties("pluginRepositoryUrl")
//}

// Configure Gradle Qodana Plugin - read more: https://github.com/JetBrains/gradle-qodana-plugin
qodana {
  cachePath = provider { file(".qodana").canonicalPath }
  reportPath = provider { file("build/reports/inspections").canonicalPath }
  saveReport = true
  showReport = environment("QODANA_SHOW_REPORT").map { it.toBoolean() }.getOrElse(false)
}

// Configure Gradle Kover Plugin - read more: https://github.com/Kotlin/kotlinx-kover#configuration
koverReport {
  defaults {
    xml {
      onCheck = true
    }
  }
}

tasks {
  wrapper {
    gradleVersion = properties("gradleVersion").get()
  }

  patchPluginXml {
    version = properties("pluginVersion")
    sinceBuild = properties("pluginSinceBuild")
    untilBuild = properties("pluginUntilBuild")

    // Extract the <!-- Plugin description --> section from README.md and provide for the plugin's manifest
    pluginDescription = providers.fileContents(layout.projectDirectory.file("README.md")).asText.map {
      val start = "<!-- Plugin description -->"
      val end = "<!-- Plugin description end -->"

      with(it.lines()) {
        if (!containsAll(listOf(start, end))) {
          throw GradleException("Plugin description section not found in README.md:\n$start ... $end")
        }
        subList(indexOf(start) + 1, indexOf(end)).joinToString("\n").let(::markdownToHTML)
      }
    }

    val changelog = project.changelog // local variable for configuration cache compatibility
    // Get the latest available change notes from the changelog file
    changeNotes = properties("pluginVersion").map { pluginVersion ->
      with(changelog) {
        renderItem(
          (getOrNull(pluginVersion) ?: getUnreleased())
            .withHeader(false)
            .withEmptySections(false),
          Changelog.OutputType.HTML,
        )
      }
    }
  }

  // Configure UI tests plugin
  // Read more: https://github.com/JetBrains/intellij-ui-test-robot
  runIdeForUiTests {
    systemProperty("robot-server.port", "8082")
    systemProperty("ide.mac.message.dialogs.as.sheets", "false")
    systemProperty("jb.privacy.policy.text", "<!--999.999-->")
    systemProperty("jb.consents.confirmation.enabled", "false")
  }

  signPlugin {
    certificateChain = environment("CERTIFICATE_CHAIN")
    privateKey = environment("PRIVATE_KEY")
    password = environment("PRIVATE_KEY_PASSWORD")
  }

  publishPlugin {
    dependsOn("patchChangelog")
    token = environment("PUBLISH_TOKEN")
    // The pluginVersion is based on the SemVer (https://semver.org) and supports pre-release labels, like 2.1.7-alpha.3
    // Specify pre-release label to publish the plugin in a custom Release Channel automatically. Read more:
    // https://plugins.jetbrains.com/docs/intellij/deployment.html#specifying-a-release-channel
    channels = properties("pluginVersion").map { listOf(it.split('-').getOrElse(1) { "default" }.split('.').first()) }
  }
}


tasks {
  withType<JavaCompile> {
    sourceCompatibility = "17"
    targetCompatibility = "17"
  }
  withType<KotlinCompile> {
    kotlinOptions.jvmTarget = "17"
  }
  withType<org.jetbrains.intellij.tasks.BuildSearchableOptionsTask>()
    .forEach { it.enabled = false }
}

tasks.withType<RunIdeTask> {
  jvmArgs("--add-exports", "java.base/jdk.internal.vm=ALL-UNNAMED", "-Xmx16000m", "-Xms8192m")
}

tasks.create<RunIdeTask>("headless") {
  val prj: String? by project
  val out: String? by project
  args = listOfNotNull("busFactor", prj, out)
  jvmArgs(
    "-Xmx16G",
    "-Djava.awt.headless=true",
    "--add-exports",
    "java.base/jdk.internal.vm=ALL-UNNAMED"
  )
}
