package org.jetbrains.research.ictl.riskypatterns.inspections.logic

import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.containers.mapSmartSet
import org.jetbrains.research.ictl.riskypatterns.calculation.BusFactor
import org.jetbrains.research.ictl.riskypatterns.services.BusFactorService
import org.jetbrains.research.ictl.riskypatterns.settings.BusFactorConfigService

data class MinorInfo(
  val minorContributors: Set<Int>,
  val numOfContributors: Int,
  val sumAuthorship: Double
)

object MinorContributorsLogic {
  fun isMinor(file: VirtualFile): Boolean {
    if (file.isDirectory) return false
    val service = BusFactorService.instance
    val config = BusFactorConfigService.instance
    if (!service.isValidFilePath(file)) return false

    ReadAction.compute<Double?, RuntimeException> { getMinorPartition(file) }
      ?.let { minorPart ->
        return minorPart >= config.minorContributorsThreshold
      }
    return false
  }

  fun isMinor(filePath: String): Boolean? {
    val service = BusFactorService.instance
    if (!service.isValidFilePath(filePath)) return false
    ReadAction.compute<Double?, RuntimeException> { getMinorPartition(filePath) }
      ?.let { minorPart ->
        val config = BusFactorConfigService.instance
        return minorPart >= config.minorContributorsThreshold
      }
    return null
  }


  fun getMinorContributors(vFile: VirtualFile): Set<String>? = getMinorContributors(vFile.path)

  fun getMinorContributors(filePath: String): Set<String>? {
    val service = BusFactorService.instance
    val context = service.context
    val projectPath = service.projectPath
    val filePathTrimmed = filePath.removePrefix(projectPath)
    val minorInfo = getMinorInfo(filePathTrimmed) ?: return null
    return minorInfo.minorContributors.mapSmartSet { context.userMapper.idToEntity[it]!! }
  }

  fun getMinorPartition(vFile: VirtualFile): Double? =
    getMinorPartition(vFile.path)


  fun getMinorPartition(filePath: String): Double? {
    val projectPath = BusFactorService.instance.projectPath
    val filePathTrimmed = filePath.removePrefix(projectPath)
    val minorInfo = getMinorInfo(filePathTrimmed) ?: return null
    return minorInfo.minorContributors.size.toDouble() / minorInfo.numOfContributors
  }

  private fun getMinorInfo(filePath: String): MinorInfo? {
    val context = BusFactorService.instance.context
    val fileId = context.fileMapper.entityToId[filePath] ?: return null
    val info = context.filesOwnership[fileId] ?: return null

    var sumAuthorship = 0.0
    info.values.forEach { sumAuthorship += it.authorship }
    val minorContributors = mutableSetOf<Int>()
    info.forEach { if (BusFactor.isMinorContributor(it.value.authorship, sumAuthorship)) minorContributors.add(it.key) }

    return MinorInfo(
      minorContributors,
      info.size,
      sumAuthorship
    )
  }

}