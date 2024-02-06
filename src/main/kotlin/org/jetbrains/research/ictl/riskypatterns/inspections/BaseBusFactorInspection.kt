package org.jetbrains.research.ictl.riskypatterns.inspections

import com.intellij.analysis.AnalysisScope
import com.intellij.codeInspection.*
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiManager
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.SearchScope
import git4idea.repo.GitRepositoryManager
import org.jetbrains.research.ictl.riskypatterns.services.BusFactorService
import org.jetbrains.research.ictl.riskypatterns.settings.BusFactorConfigService

abstract class BaseBusFactorInspection : GlobalInspectionTool() {

  companion object {
    fun checkPreviousSettings(project: Project): Boolean {
      val repositories = GitRepositoryManager.getInstance(project).repositories
      val gitRepository = repositories.first()
      val service = BusFactorService.instance

      if (!service.isSameSettings(gitRepository)) {
        NotificationGroupManager.getInstance()
          .getNotificationGroup("BF Notification Group")
          .createNotification("Recalculating bus factor state. Launch inspection after calculation.", NotificationType.WARNING)
          .notify(project);
        ApplicationManager.getApplication().invokeLater {
          service.mineRepositoryPM(gitRepository)
        }
        return false
      }
      return true
    }
  }

  override fun isGraphNeeded(): Boolean {
    return false
  }

  override fun isReadActionNeeded(): Boolean {
    return false
  }

  override fun runInspection(
    scope: AnalysisScope,
    manager: InspectionManager,
    globalContext: GlobalInspectionContext,
    problemDescriptionsProcessor: ProblemDescriptionsProcessor
  ) {
    val project = globalContext.project
    if (!checkPreviousSettings(project)) return

    val index = ProjectRootManager.getInstance(project).fileIndex
    val searchScope = ReadAction.compute<SearchScope, RuntimeException> { scope.toSearchScope() } as? GlobalSearchScope
      ?: return

    val refManager = globalContext.refManager
    val psiManager = PsiManager.getInstance(project)
    index.iterateContent({ vFile: VirtualFile ->
      runReadAction {
        val service = BusFactorService.instance
        val config = BusFactorConfigService.instance
        inspectVirtualFile(manager, service, config, project, vFile)?.let { problemDescriptors ->
          val psiFile = psiManager.findFile(vFile)
          val ref = refManager.getReference(psiFile)
          problemDescriptors.forEach {
            problemDescriptionsProcessor.addProblemElement(ref, it)
          }
        }
      }
      true
    }, searchScope)
  }

  abstract fun inspectVirtualFile(
    manager: InspectionManager,
    service: BusFactorService,
    config: BusFactorConfigService,
    project: Project,
    vFile: VirtualFile
  ): Collection<CommonProblemDescriptor>?

}