package org.jetbrains.research.ictl.riskypatterns.processors

import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.vcs.changes.Change
import com.intellij.util.Consumer
import com.intellij.vcs.log.VcsUser
import git4idea.GitCommit
import org.jetbrains.research.ictl.riskypatterns.RiskyPatternsBundle
import org.jetbrains.research.ictl.riskypatterns.calculation.BusFactor
import org.jetbrains.research.ictl.riskypatterns.calculation.entities.CommitInfo
import org.jetbrains.research.ictl.riskypatterns.calculation.entities.DiffEntry
import org.jetbrains.research.ictl.riskypatterns.calculation.entities.UserInfo
import org.jetbrains.research.ictl.riskypatterns.services.BusFactorService

class BusFactorCommitConsumer(
  private val busFactor: BusFactor,
  private val numberOfCommits: Int,
  private val projectPath: String,
  private val progressIndicator: ProgressIndicator? = null
) :
  Consumer<GitCommit> {

  companion object {
    fun convertToCommitInfo(gitCommit: GitCommit, projectPath: String) = CommitInfo(
      convertToUserInfo(gitCommit.author),
      convertToUserInfo(gitCommit.committer),
      gitCommit.authorTime,
      gitCommit.commitTime,
      gitCommit.changes.map { convertChange(it, projectPath) },
      gitCommit.parents.size,
      gitCommit.fullMessage,
      gitCommit.id.asString()
    )

    private fun convertToUserInfo(user: VcsUser) = UserInfo(user.name, user.email)

    private fun convertChange(change: Change, projectPath: String): DiffEntry {
      val oldPath = trimFilePath(change.beforeRevision?.file?.path, projectPath) ?: ""
      val newPath = trimFilePath(change.afterRevision?.file?.path, projectPath) ?: ""
      val changeType = when (change.type) {
        Change.Type.NEW -> DiffEntry.ChangeType.ADD
        Change.Type.MODIFICATION -> DiffEntry.ChangeType.MODIFY
        Change.Type.DELETED -> DiffEntry.ChangeType.DELETE
        Change.Type.MOVED -> DiffEntry.ChangeType.COPY
      }
      return DiffEntry(oldPath, newPath, changeType)
    }

    fun trimFilePath(filePath: String?, projectPath: String) = filePath?.removePrefix(projectPath)
  }

  private val stepFraction = (1.0 / numberOfCommits) * BusFactorService.MINING_FRACTION
  private var commitsProceeded = 0

  @Throws(ProcessCanceledException::class)
  override fun consume(commit: GitCommit) {
    if (commit.parents.size > 1) return


    busFactor.consumeCommit(convertToCommitInfo(commit, projectPath))
    progressIndicator?.let {
      it.checkCanceled()
      it.fraction += stepFraction
      it.text = RiskyPatternsBundle.message("mining") + " $commitsProceeded / $numberOfCommits"
      commitsProceeded++
    }
  }

}
