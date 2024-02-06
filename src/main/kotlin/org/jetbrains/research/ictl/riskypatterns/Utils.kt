package org.jetbrains.research.ictl.riskypatterns

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.util.io.FileUtilRt
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.research.ictl.riskypatterns.calculation.*
import org.jetbrains.research.ictl.riskypatterns.calculation.processors.CommitProcessor
import org.jetbrains.research.ictl.riskypatterns.services.BusFactorService
import org.jetbrains.research.ictl.riskypatterns.visualization.ReportBuilder
import java.lang.reflect.Field
import java.lang.reflect.Type
import java.util.*
import java.util.stream.Collectors
import java.util.stream.Stream
import kotlin.collections.ArrayDeque

object Utils {
  fun version(): String {
    return RiskyPatternsBundle.message("version") + Stream.of<Class<out Any?>>(
      BusFactor::class.java,
      BusFactorCalculation::class.java,
      BusFactorComputationContext::class.java,
      BusFactorConstants::class.java,
      ContributionsByUser::class.java,
      BusFactorService::class.java,
      CommitProcessor::class.java,
      ReportBuilder::class.java,
    ).flatMap { c: Class<out Any?> ->
      Arrays.stream(c.declaredFields)
        .map { obj: Field -> obj.genericType }
        .map { obj: Type -> obj.typeName }
    }.collect(Collectors.toList()).toString().hashCode()
  }

  fun getFileNames(virtualFile: VirtualFile, projectPath: String): List<String> {
    val result = mutableListOf<String>()
    val queue = ArrayDeque<VirtualFile>()
    queue.add(virtualFile)
    while (queue.isNotEmpty()) {
      val node = queue.removeLast()
      val children = node.children
      if (children.isEmpty()) {
        result.add(node.path.removePrefix(projectPath))
      } else {
        queue.addAll(children)
      }
    }
    return result
  }

  fun getDirs(virtualFile: VirtualFile): List<VirtualFile> {
    val result = mutableListOf<VirtualFile>()
    val queue = ArrayDeque<VirtualFile>()
    queue.add(virtualFile)
    while (queue.isNotEmpty()) {
      val node = queue.removeLast()
      val children = node.children
      if (children.isNotEmpty()) {
        result.add(node)
        queue.addAll(children)
      }
    }
    return result
  }

  fun getContextFiles(e: AnActionEvent): Array<VirtualFile> {
    return e.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY) ?: return VirtualFile.EMPTY_ARRAY
  }

  fun isValidFilePath(filePath: String, ignoreExtensions: Set<String>): Boolean {
    val extension = FileUtilRt.getExtension(filePath, null) ?: return true
    return extension !in ignoreExtensions
  }

  fun isValidFilePath(vFile: VirtualFile, ignoreExtensions: Set<String>): Boolean =
    if (vFile.isDirectory) true else isValidFilePath(vFile.path, ignoreExtensions)

}