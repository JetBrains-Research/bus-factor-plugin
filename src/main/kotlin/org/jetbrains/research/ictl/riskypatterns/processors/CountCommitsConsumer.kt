package org.jetbrains.research.ictl.riskypatterns.processors

import com.intellij.util.Consumer
import git4idea.GitCommit
import org.jetbrains.research.ictl.riskypatterns.calculation.entities.UserInfo


class CountCommitsConsumer : Consumer<GitCommit> {
  var count = 0
    private set

  private val _users = mutableSetOf<UserInfo>()
  val users: Set<UserInfo>
    get() = _users

  override fun consume(commit: GitCommit) {
    _users.add(UserInfo(commit.author.name, commit.author.email))
    _users.add(UserInfo(commit.committer.name, commit.committer.email))
    count++
  }
}