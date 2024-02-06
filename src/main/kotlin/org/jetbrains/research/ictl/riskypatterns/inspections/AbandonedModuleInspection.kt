package org.jetbrains.research.ictl.riskypatterns.inspections

import com.intellij.analysis.AnalysisScope
import com.intellij.codeInspection.GlobalInspectionContext
import com.intellij.codeInspection.GlobalInspectionTool
import com.intellij.codeInspection.InspectionManager
import com.intellij.codeInspection.ProblemDescriptionsProcessor
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiManager
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.SearchScope
import org.jetbrains.research.ictl.riskypatterns.inspections.logic.AbandonedModuleLogic
import org.jetbrains.research.ictl.riskypatterns.settings.BusFactorConfigService


class AbandonedModuleInspection : GlobalInspectionTool() {

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
    if (!BaseBusFactorInspection.checkPreviousSettings(project)) return

    val index = ProjectRootManager.getInstance(project).fileIndex
    val searchScope = ReadAction.compute<SearchScope, RuntimeException> { scope.toSearchScope() } as? GlobalSearchScope
      ?: return

    val modulePaths = mutableSetOf<String>()
    runReadAction {
      val moduleManager = ModuleManager.getInstance(project)
      val ideaModules = moduleManager.modules
      ideaModules.forEach { module ->
        val moduleRootManager = ModuleRootManager.getInstance(module)
        moduleRootManager.contentRoots.forEach { contentRoot ->
          contentRoot.canonicalPath?.let {
            modulePaths.add(it)
          }
        }
      }
    }

    runReadAction {
      val refManager = globalContext.refManager
      val psiManager = PsiManager.getInstance(project)
      index.iterateContent({ vFile: VirtualFile ->
        val isIgnored = !BusFactorConfigService.instance.isValidFilePath(vFile)
        if (isIgnored) return@iterateContent true

        if (vFile.canonicalPath !in modulePaths) {
          return@iterateContent true
        }

        val directory =
          ReadAction.compute<PsiDirectory?, RuntimeException> {
            psiManager.findDirectory(vFile)
          }

        val (isAbandoned, abandonmentPartition) = AbandonedModuleLogic.isAbandonedDirectoryAndValue(vFile)
        if (isAbandoned) {
          refManager.getReference(directory)?.let { ref ->
            val problem = manager.createProblemDescriptor(
              "Module seems to be abandoned. Abandonment partition: $abandonmentPartition"
            )
            problemDescriptionsProcessor.addProblemElement(ref, problem)
          }
        }

        true
      }, searchScope)
    }

  }
}
