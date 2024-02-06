package org.jetbrains.research.ictl.riskypatterns.inspections

import com.intellij.codeInspection.CommonProblemDescriptor
import com.intellij.codeInspection.InspectionManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.research.ictl.riskypatterns.inspections.logic.AbandonedFilesLogic
import org.jetbrains.research.ictl.riskypatterns.services.BusFactorService
import org.jetbrains.research.ictl.riskypatterns.settings.BusFactorConfigService

class NoDataAboutFilesInspection : BaseBusFactorInspection() {
  override fun inspectVirtualFile(
    manager: InspectionManager,
    service: BusFactorService,
    config: BusFactorConfigService,
    project: Project,
    vFile: VirtualFile
  ): Collection<CommonProblemDescriptor>? {
    val isIgnored = !BusFactorConfigService.instance.isValidFilePath(vFile)
    if (isIgnored) return null

    AbandonedFilesLogic.isAbandonedFile(vFile)
      ?: return listOf(manager.createProblemDescriptor("File seems to be abandoned."))
    return null
  }
}
