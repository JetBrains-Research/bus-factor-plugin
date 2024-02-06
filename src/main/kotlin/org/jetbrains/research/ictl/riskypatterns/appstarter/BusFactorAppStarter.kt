package org.jetbrains.research.ictl.riskypatterns.appstarter

import com.intellij.ide.impl.ProjectUtil
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ApplicationStarter
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.ProjectManager
import com.intellij.vcs.log.impl.VcsProjectLog
import git4idea.repo.GitRepositoryManager
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.jetbrains.research.ictl.riskypatterns.services.BusFactorService
import java.io.File
import kotlin.system.exitProcess

class BusFactorAppStarter : ApplicationStarter {

  @Deprecated("Specify it as `id` for extension definition in a plugin descriptor")
  override val commandName: String = "busFactor"

  override fun main(args: List<String>) {
    val projectPath = args[1]
    val outputFile = File(args[2])
    val project = ProjectUtil.openOrImport(projectPath, null, true) ?: run {
      exitProcess(1)
    }
    DumbService.getInstance(project).runWhenSmart {
      VcsProjectLog.runWhenLogIsReady(project) {
        ApplicationManager.getApplication().runWriteAction {
          val repositories = GitRepositoryManager.getInstance(project).repositories
          val repository = repositories.first()
          val service = BusFactorService.instance
          service.mineRepositoryWithoutPM(repository, projectPath)
          val jsonState = Json.encodeToString(service.state)
          outputFile.writeText(jsonState)
        }
        ProjectManager.getInstance().closeAndDispose(project)
        exitProcess(0)
      }
    }
  }

}