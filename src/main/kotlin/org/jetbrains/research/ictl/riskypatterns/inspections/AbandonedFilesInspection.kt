package org.jetbrains.research.ictl.riskypatterns.inspections

import com.intellij.codeInspection.CommonProblemDescriptor
import com.intellij.codeInspection.InspectionManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.research.ictl.riskypatterns.inspections.logic.AbandonedFilesLogic
import org.jetbrains.research.ictl.riskypatterns.services.BusFactorService
import org.jetbrains.research.ictl.riskypatterns.settings.BusFactorConfigService


class AbandonedFilesInspection : BaseBusFactorInspection() {
  override fun inspectVirtualFile(
    manager: InspectionManager,
    service: BusFactorService,
    config: BusFactorConfigService,
    project: Project,
    vFile: VirtualFile
  ): Collection<CommonProblemDescriptor>? {
    val isIgnored = !BusFactorConfigService.instance.isValidFilePath(vFile)
    if (isIgnored) return null

    val isAbandoned = AbandonedFilesLogic.isAbandonedFile(vFile)
    if (isAbandoned == null || isAbandoned == false) return null
    return listOf(manager.createProblemDescriptor("File seems to be abandoned."))
  }
}