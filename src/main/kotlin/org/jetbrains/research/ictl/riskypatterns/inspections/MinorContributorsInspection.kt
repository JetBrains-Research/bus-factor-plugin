package org.jetbrains.research.ictl.riskypatterns.inspections

import com.intellij.codeInspection.CommonProblemDescriptor
import com.intellij.codeInspection.InspectionManager
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ui.MultipleCheckboxOptionsPanel
import com.intellij.openapi.editor.Document
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.util.DocumentUtil
import com.intellij.vcsUtil.VcsUtil
import git4idea.GitUtil
import git4idea.commands.GitBinaryHandler
import git4idea.commands.GitCommand
import git4idea.util.StringScanner
import org.jetbrains.research.ictl.riskypatterns.inspections.logic.MinorContributorsLogic
import org.jetbrains.research.ictl.riskypatterns.services.BusFactorService
import org.jetbrains.research.ictl.riskypatterns.settings.BusFactorConfigService
import java.nio.charset.StandardCharsets
import javax.swing.JComponent

class MinorContributorsInspection : BaseBusFactorInspection() {
  companion object {
    private const val AUTHOR_EMAIL_TOKEN = "author-mail"
  }

  @JvmField
  var useHighlighting: Boolean = false

  override fun inspectVirtualFile(
    manager: InspectionManager,
    service: BusFactorService,
    config: BusFactorConfigService,
    project: Project,
    vFile: VirtualFile
  ): Collection<CommonProblemDescriptor>? {
    val isIgnored = !BusFactorConfigService.instance.isValidFilePath(vFile)
    if (isIgnored) return null

    val isMinor = MinorContributorsLogic.isMinor(vFile)
    if (!isMinor) return null
    if (!useHighlighting) {
      return defaultWarning(manager)
    }

    val minorContributors = MinorContributorsLogic.getMinorContributors(vFile) ?: return null
    val minorContributorsLines = gitBlame(project, vFile, minorContributors)
    if (minorContributorsLines.isEmpty()) {
      return defaultWarning(manager)
    }

    val psiManager = PsiManager.getInstance(project)
    val psiFile = psiManager.findFile(vFile)!!
    val document = PsiDocumentManager.getInstance(project).getDocument(psiFile)!!

    val result = mutableListOf<ProblemDescriptor>()
    for ((_, lines) in minorContributorsLines) {
      var firstLine: Int = -1
      var lastLine: Int = -1
      for ((index, line) in lines.sorted().withIndex()) {
        if (index == 0) {
          firstLine = line
          lastLine = line
        } else {
          if (lastLine + 1 == line) {
            lastLine = line
          } else {
            result.add(createHighlightProblem(psiFile, document, manager, firstLine, lastLine))
            firstLine = line
            lastLine = line
          }
        }

        if (index == lines.size - 1) {
          result.add(createHighlightProblem(psiFile, document, manager, firstLine, lastLine))
        }
      }
    }
    return result
  }

  override fun createOptionsPanel(): JComponent = MultipleCheckboxOptionsPanel(this).also {
    it.addCheckbox("Use line highlighting", "useHighlighting")
  }

  private fun defaultWarning(manager: InspectionManager) = listOf(
    manager.createProblemDescriptor(
      "There are more than a half of developers with authorship 5% and less."
    )
  )

  private fun gitBlame(
    project: Project,
    vFile: VirtualFile,
    minorContributors: Set<String>
  ): HashMap<String, MutableSet<Int>> {
    val root: VirtualFile = GitUtil.getRootForFile(project, vFile)

    val h = GitBinaryHandler(project, root, GitCommand.BLAME)
    h.setStdoutSuppressed(true)
    h.addParameters("--porcelain", "-l", "-t")
    h.addParameters("--encoding=UTF-8")
    h.addParameters("HEAD")
    h.endOptions()
    val filePath = VcsUtil.getLastCommitPath(project, VcsUtil.getFilePath(vFile))
    h.addRelativePaths(filePath)
    val output = String(h.run(), StandardCharsets.UTF_8)
    val s = StringScanner(output)
    val lines = HashMap<String, MutableSet<Int>>()
    var authorEmail: String? = null
    while (s.hasMoreData()) {
      s.spaceToken()
      s.spaceToken()
      val lineNum = s.spaceToken().toInt()
      s.nextLine()

      while (s.hasMoreData() && !s.startsWith('\t')) {
        val key = s.spaceToken()
        val value = s.line()
        if (key == AUTHOR_EMAIL_TOKEN) {
          authorEmail = value.removePrefix("<").removeSuffix(">")
        }
      }
      if (authorEmail in minorContributors)
        lines.computeIfAbsent(authorEmail!!) { mutableSetOf() }.add(lineNum)
      // skip file line
      s.nextLine()

    }
    return lines
  }

  private fun createHighlightProblem(
    psiFile: PsiFile,
    document: Document,
    manager: InspectionManager,
    firstLine: Int,
    lastLine: Int
  ): ProblemDescriptor {
    val textRange = if (firstLine == lastLine) DocumentUtil.getLineTextRange(document, firstLine - 1) else {
      val start = DocumentUtil.getLineTextRange(document, firstLine - 1)
      val end = DocumentUtil.getLineTextRange(document, lastLine - 1)
      TextRange(start.startOffset, end.endOffset)
    }
    return manager.createProblemDescriptor(
      psiFile as PsiElement,
      textRange,
      "Minor contributor lines $firstLine-$lastLine",
      ProblemHighlightType.WARNING,
      true
    )
  }
}
