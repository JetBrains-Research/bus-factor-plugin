package org.jetbrains.research.ictl.riskypatterns.inspections.logic

import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.research.ictl.riskypatterns.calculation.BFContext
import org.jetbrains.research.ictl.riskypatterns.calculation.BusFactor
import org.jetbrains.research.ictl.riskypatterns.services.BusFactorService

object AbandonedFilesLogic {
  fun isAbandonedFile(
    file: VirtualFile
  ): Boolean? {
    if (file.isDirectory) return false
    return isAbandonedFile(file.path)
  }

  fun isAbandonedFile(filePath: String): Boolean? {
    val service = BusFactorService.instance
    val context: BFContext = service.context
    val projectPath = service.projectPath
    val filePathTrimmed = filePath.removePrefix(projectPath)
    val ignoreExtensions = context.configSnapshot.ignoreExtensions
    if (!BusFactor.isValidFilePath(filePathTrimmed, ignoreExtensions)) return false
    val fileId = context.fileMapper.entityToId[filePathTrimmed] ?: return null
    val info = context.filesOwnership[fileId] ?: return null
    val (userId, _) = context.weightedOwnership[fileId] ?: return null
    val authorship = info[userId]?.authorship ?: return null
    return authorship < 1.0
  }
}
