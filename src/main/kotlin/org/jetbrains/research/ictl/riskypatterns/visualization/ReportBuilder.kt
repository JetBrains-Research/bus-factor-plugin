package org.jetbrains.research.ictl.riskypatterns.visualization

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.fileEditor.impl.HTMLEditorProvider
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.project.isDirectoryBased
import com.intellij.project.stateStore
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.jetbrains.research.ictl.riskypatterns.calculation.BusFactorCalculation
import org.jetbrains.research.ictl.riskypatterns.calculation.BusFactorConfigSnapshot
import org.jetbrains.research.ictl.riskypatterns.calculation.entities.Tree
import org.jetbrains.research.ictl.riskypatterns.calculation.entities.UserVis
import org.jetbrains.research.ictl.riskypatterns.services.BusFactorService
import java.io.*
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream


object ReportBuilder {
  @Suppress("PROVIDED_RUNTIME_TOO_LOW")
  @Serializable
  data class ReportMetaInfo(
    val lastCommit: String,
    val projectPath: String,
    val version: String,
    val configSnapshot: BusFactorConfigSnapshot
  ) {
    companion object {
      fun currentMetaInfo(): ReportMetaInfo {
        val service = BusFactorService.instance
        return ReportMetaInfo(
          service.lastCommitHash!!,
          service.projectPath,
          service.version,
          service.context.configSnapshot
        )
      }
    }
  }

  private fun unzipResource(resultDir: File) {
    val inputStream: InputStream = ReportBuilder::class.java.getResourceAsStream("/report/build.zip")
    val zis = ZipInputStream(inputStream)
    var entry: ZipEntry?
    val buffer = ByteArray(1024)
    while (zis.getNextEntry().also { entry = it } != null) {
      val newFile = File(resultDir, entry!!.name)
      if (entry!!.isDirectory) {
        if (!newFile.isDirectory() && !newFile.mkdirs()) {
          throw IOException("Failed to create directory $newFile")
        }
      } else {
        val parent = newFile.getParentFile()
        if (!parent.isDirectory() && !parent.mkdirs()) {
          throw IOException("Failed to create directory $parent")
        }

        val fos = FileOutputStream(newFile)
        var len: Int
        while (zis.read(buffer).also { len = it } > 0) {
          fos.write(buffer, 0, len)
        }
        fos.close()
      }
    }
  }

  private fun buildTree(treeName: String, project: Project): Tree {
    val root = Tree(treeName, ".")
    var allSize = 0L

    val projectPath = project.basePath!!
    ProjectFileIndex.getInstance(project).iterateContent { it ->
      val filePath = it.path.removePrefix(projectPath).removePrefix("/")
      if (filePath.isEmpty()) return@iterateContent true

      val bytes = it.length
      val parts = filePath.split("/")
      var node = root
      var path = ""
      for (part in parts) {
        if (path.isEmpty()) path = part else path += "/$part"
        node = node.children.find { it.name == part } ?: run {
          val newNode = Tree(part, path, bytes)
          node.children.add(newNode)
          newNode
        }
      }
      allSize += bytes
      true
    }
    root.bytes = allSize
    calculateBusFactorForTree(root)
    return root
  }

  private fun calculateBusFactorForTree(root: Tree) {
    val context = runReadAction { BusFactorService.instance.state.context }
    val busFactorCalculation = BusFactorCalculation(context)
    val queue = ArrayDeque<Tree>()
    queue.add(root)

    while (queue.isNotEmpty()) {
      val node = queue.removeLast()

      val children = node.children
      if (children.isNotEmpty()) {
        queue.addAll(children)
      }

      // TODO: fix for all cases of trimmed path
      val fileNames = node.getFileNames().map { "/$it" }
      val userStats = busFactorCalculation.userStats(fileNames)
      val busFactorCalculationResult = busFactorCalculation.computeBusFactorForFiles(fileNames)

      node.busFactorStatus = busFactorCalculationResult.busFactorStatus
      node.users = UserVis.convert(userStats, busFactorCalculationResult.developersSorted)
    }
  }


  private fun metaInfoFile(project: Project): File {
    val resultDir = getResultDir(project)
    return File(resultDir, "meta")
  }

  private fun isOldReport(project: Project): Boolean {
    val metaFile = metaInfoFile(project)
    if (!metaFile.isFile) return true
    val metaInfo = Json.decodeFromString<ReportMetaInfo>(metaFile.readText())
    val currentMetaInfo = ReportMetaInfo.currentMetaInfo()
    return metaInfo != currentMetaInfo
  }

  fun buildReportAndSave(project: Project) {
    if (!isOldReport(project)) return

    val service = BusFactorService.instance
    val context = service.context
    val projectPath = service.projectPath
    val resultDir = getResultDir(project)
    resultDir.mkdirs()
    val stringBuilder = StringBuilder()

    val reportPath = "/report"
    val part1 = BusFactorService::class.java.getResource("${reportPath}/1")!!.readText()
    stringBuilder.append(part1)
    val treeName = File(projectPath).name
    val tree = buildTree(treeName, project)
    val jsonTree = Json.encodeToString(tree)
    stringBuilder.append(jsonTree)
    stringBuilder.append(")),{},{emailToName:")

    val idToEmail = context.userMapper.idToEntity
    val emailToName = context.userMapper.idToName.mapKeys { idToEmail[it.key]!! }
    val jsonEmailToName = Json.encodeToString(emailToName)
    stringBuilder.append(jsonEmailToName)
    stringBuilder.append("}")

    val part2 = BusFactorService::class.java.getResource("${reportPath}/2")!!.readText()
    stringBuilder.append(part2)

    unzipResource(resultDir)
    val resultFile = File(resultDir, "/build/static/js/main.26835865.js")
    BufferedWriter(FileWriter(resultFile)).use {
      it.append(stringBuilder)
    }

    metaInfoFile(project).writeText(Json.encodeToString(ReportMetaInfo.currentMetaInfo()))
  }

  private fun getResultDir(project: Project): File {
    val store = project.stateStore
    val mainFolder = "busFactor"
    val path = store.projectFilePath.parent.resolve(if (project.isDirectoryBased) mainFolder else ".$mainFolder")
    return File(path.toString())
  }

  fun openReportInEditor(project: Project) {
    val resultDirPath = getResultDir(project).absolutePath
    ApplicationManager.getApplication().invokeLater {
      HTMLEditorProvider.openEditor(
        project,
        "Bus Factor",
        url = "file://$resultDirPath/build/index.html"
      )
    }
  }
}
