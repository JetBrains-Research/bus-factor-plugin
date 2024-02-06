package org.jetbrains.research.ictl.riskypatterns.inspections.logic

import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.research.ictl.riskypatterns.Utils
import org.jetbrains.research.ictl.riskypatterns.services.BusFactorService
import org.jetbrains.research.ictl.riskypatterns.settings.BusFactorConfigService

object AbandonedModuleLogic {
  fun isAbandonedDirectoryAndValue(
    file: VirtualFile,
  ): Pair<Boolean, Double?> {
    if (!file.isDirectory) return false to null
    getDirectoryAbandonment(file)?.let { abandonmentPartition ->
      val config = BusFactorConfigService.instance
      return (abandonmentPartition >= config.abandonmentPartitionThreshold) to abandonmentPartition
    }
    return false to null
  }

  fun getDirectoryAbandonment(virtualFile: VirtualFile): Double? {
    val service = BusFactorService.instance
    val projectPath = service.projectPath

    val filePath = virtualFile.canonicalPath!!.removePrefix(projectPath)
//    TODO: uncomment when done with non module projects
//    val ideaModules = ModuleManager.getInstance(project).modules
//    val modules = ideaModules.map { ModuleUtil.getModuleDirPath(it).removePrefix(context.projectPath) }.toSet()
    val modules = Utils.getDirs(virtualFile).map { it.path.removePrefix(projectPath) }
    if (filePath in modules) {
      var count = 0
      var abandoned = 0
      val queue = ArrayDeque<VirtualFile>()
      queue.add(virtualFile)
      while (queue.isNotEmpty()) {
        val node = queue.removeLast()
        val children = node.children
        if (children.isEmpty()) {
          val childFilePath = node.path.removePrefix(projectPath)
          if (!service.isValidFilePath(childFilePath)) continue
          count++
          if (AbandonedFilesLogic.isAbandonedFile(childFilePath) != false) abandoned++
        } else {
          queue.addAll(children)
        }
      }
      return abandoned.toDouble() / count
    } else {
      return null
    }
  }

}