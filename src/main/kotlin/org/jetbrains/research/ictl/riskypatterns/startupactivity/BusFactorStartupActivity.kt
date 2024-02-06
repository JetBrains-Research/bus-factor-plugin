package org.jetbrains.research.ictl.riskypatterns.startupactivity

import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.impl.VcsStartupActivity
import git4idea.repo.GitRepositoryManager
import org.jetbrains.research.ictl.riskypatterns.services.BusFactorService

class BusFactorStartupActivity : VcsStartupActivity {

  companion object {
    private val logger = logger<BusFactorStartupActivity>()
  }

  override fun runActivity(project: Project) {
    logger.info("Start Run in StartupActivity")
    val repositories = GitRepositoryManager.getInstance(project).repositories
    val repository = repositories.first()
    val service = BusFactorService.instance
    service.mineRepositoryPM(repository)
  }

  override fun getOrder(): Int = Int.MAX_VALUE
}