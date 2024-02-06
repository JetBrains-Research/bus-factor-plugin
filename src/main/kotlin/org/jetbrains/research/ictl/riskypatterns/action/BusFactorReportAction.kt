package org.jetbrains.research.ictl.riskypatterns.action

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction
import git4idea.repo.GitRepositoryManager
import org.jetbrains.research.ictl.riskypatterns.services.BusFactorService

class BusFactorReportAction : DumbAwareAction() {

  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project!!
    val service = BusFactorService.instance
    val repositories = GitRepositoryManager.getInstance(project).repositories
    val repository = repositories[0]

    service.mineAndSaveReport(repository)
  }
}
