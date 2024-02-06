package org.jetbrains.research.ictl.riskypatterns.services

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.components.*
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task.Backgroundable
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.xmlb.annotations.OptionTag
import git4idea.GitCommit
import git4idea.history.GitHistoryUtils
import git4idea.repo.GitRepository
import kotlinx.serialization.Serializable
import org.jetbrains.research.ictl.riskypatterns.RiskyPatternsBundle
import org.jetbrains.research.ictl.riskypatterns.Utils
import org.jetbrains.research.ictl.riskypatterns.calculation.*
import org.jetbrains.research.ictl.riskypatterns.calculation.mappers.UserMapper
import org.jetbrains.research.ictl.riskypatterns.processors.BusFactorCommitConsumer
import org.jetbrains.research.ictl.riskypatterns.processors.CountCommitsConsumer
import org.jetbrains.research.ictl.riskypatterns.services.converters.BusFactorConverter
import org.jetbrains.research.ictl.riskypatterns.settings.BusFactorConfigService
import org.jetbrains.research.ictl.riskypatterns.visualization.ReportBuilder
import java.text.SimpleDateFormat
import java.time.Duration
import java.util.*
import java.util.concurrent.atomic.AtomicBoolean


@State(name = "BusFactorState", storages = [Storage("bus_factor.xml")])
@Service(Service.Level.PROJECT)
class BusFactorService : PersistentStateComponent<BusFactorService.MyState> {
  companion object {
    private val logger = logger<BusFactorService>()

    const val MINING_FRACTION = 0.8
    const val CALCULATION_FRACTION = 1 - MINING_FRACTION

    private val running = AtomicBoolean()

    val instance: BusFactorService
      get() = service()
  }

  private var state = MyState()
  val projectPath: String
    get() = state.projectPath

  val context: BFContext
    get() = state.context

  val version: String
    get() = state.version

  val lastCommitHash: String?
    get() = state.context.lastCommitHash

  @Suppress("PROVIDED_RUNTIME_TOO_LOW")
  @Serializable
  class MyState(
    @OptionTag val projectPath: String = "",
    @OptionTag var branch: String = "",
    @OptionTag(converter = BusFactorConverter::class) val context: BusFactorComputationContext = BusFactorComputationContext(
      BusFactorConfigService.instance.snapshot().busFactorConfigSnapshot,
    ),
  ) {
    @OptionTag
    val version = Utils.version()
  }

  override fun getState(): MyState {
    return state
  }

  override fun loadState(state: MyState) {
    if (Utils.version() == state.version) {
      this.state = state
    } else {
      this.state = MyState()
    }
  }

  fun mineRepositoryPM(
    gitRepository: GitRepository
  ) {
    if (!running.compareAndSet(false, true)) return

    val project = gitRepository.project
    val projectPath = project.basePath!!

    ProgressManager.getInstance()
      .run(object : Backgroundable(
        project,
        RiskyPatternsBundle.message("mining"),
        true
      ) {
        override fun run(progressIndicator: ProgressIndicator) {
          try {
            progressIndicator.text = RiskyPatternsBundle.message("mining")
            progressIndicator.isIndeterminate = false

            val branch = gitRepository.currentBranch?.fullName!!
            mineRepository(gitRepository, projectPath, progressIndicator)?.let { context ->
              setState(projectPath, branch, context)
            }

            progressIndicator.checkCanceled()
            progressIndicator.text = RiskyPatternsBundle.message("finished")
            progressIndicator.fraction = 1.0
          } finally {
            running.set(false)
          }
        }
      })
  }

  fun mineAndSaveReport(
    gitRepository: GitRepository
  ) {
    if (!running.compareAndSet(false, true)) return

    val project = gitRepository.project
    val projectPath = project.basePath!!

    ProgressManager.getInstance()
      .run(object : Backgroundable(
        project,
        RiskyPatternsBundle.message("report"),
        true
      ) {
        override fun run(progressIndicator: ProgressIndicator) {
          try {
            progressIndicator.text = RiskyPatternsBundle.message("mining")
            progressIndicator.isIndeterminate = false

            val branch = gitRepository.currentBranch?.fullName!!
            val context = mineRepository(gitRepository, projectPath, progressIndicator)

            if (context != null) {
              setState(projectPath, branch, context) {
                progressIndicator.text = RiskyPatternsBundle.message("report")
                ReportBuilder.buildReportAndSave(project)
                ReportBuilder.openReportInEditor(project)
              }
            } else {
              progressIndicator.text = RiskyPatternsBundle.message("report")
              ReportBuilder.buildReportAndSave(project)
              ReportBuilder.openReportInEditor(project)
            }

            progressIndicator.checkCanceled()
            progressIndicator.text = RiskyPatternsBundle.message("report_finish")
            progressIndicator.fraction = 1.0
          } finally {
            running.set(false)
          }

        }
      })
  }

  private fun mineRepository(
    gitRepository: GitRepository,
    projectPath: String,
    progressIndicator: ProgressIndicator? = null
  ): BusFactorComputationContext? {
    val project = gitRepository.project
    val root = gitRepository.root
    val commits = GitHistoryUtils.history(project, root, "-1")
    if (commits.isEmpty()) return null

    val lastCommit = commits.first()
    if (isSameSettings(gitRepository)) {
      logger.info("Same settings. Calculation is stopped")
      return null
    }
    return cleanRun(project, projectPath, root, lastCommit, progressIndicator)
  }

  fun isSameSettings(gitRepository: GitRepository): Boolean {
    val project = gitRepository.project
    val root = gitRepository.root
    val lastCommitHash = GitHistoryUtils.history(project, root, "-1").first().id.asString()

    val projectPath = project.basePath!!
    val previousProjectPath = state.projectPath
    val isSameProject = previousProjectPath == projectPath

    val previousBranch = state.branch
    val currentBranch = gitRepository.currentBranch?.fullName!!
    val isSameBranch = previousBranch == currentBranch

    val currentSettings = BusFactorConfigService.instance.snapshot().busFactorConfigSnapshot
    val previousSettings = state.context.configSnapshot
    val isSameSettings = previousSettings == currentSettings

    val previousLatestCommitHash = state.context.lastCommitHash
    val isSameLastCommit = previousLatestCommitHash == lastCommitHash

    return isSameProject && isSameSettings && isSameLastCommit && isSameBranch
  }

  fun mineRepositoryWithoutPM(
    gitRepository: GitRepository,
    projectPath: String
  ) {
    if (!running.compareAndSet(false, true)) return

    try {
      val branch = gitRepository.currentBranch?.fullName!!
      mineRepository(gitRepository, projectPath, null)?.let { context ->
        setState(projectPath, branch, context)
      }
    } finally {
      running.set(false)
    }
  }

  private fun cleanRun(
    project: Project,
    projectPath: String,
    root: VirtualFile,
    lastCommit: GitCommit,
    progressIndicator: ProgressIndicator? = null
  ): BusFactorComputationContext {
    logger.info("Fresh start mining")
    val afterDate = afterDate(lastCommit)
    val formatter = SimpleDateFormat("yyyy-MM-dd")

    val parameters = arrayOf("--after", formatter.format(afterDate))
    val countCommitsConsumer = countCommits(project, root, *parameters)
    val numberOfCommits = countCommitsConsumer.count
    val users = countCommitsConsumer.users

    val botFilter = BotFilter()
    val merger = UserMerger(botFilter)
    val mergedUsers = merger.mergeUsers(users)

    val context = runReadAction {
      val configSnapshot = BusFactorConfigService.instance.snapshot().busFactorConfigSnapshot
      BusFactorComputationContext(
        configSnapshot, UserMapper(botFilter, mergedUsers)
      )
    }

    val busFactor = BusFactor(context)
    busFactor.setLastCommit(BusFactorCommitConsumer.convertToCommitInfo(lastCommit, projectPath))

    val commitConsumer = BusFactorCommitConsumer(busFactor, numberOfCommits, projectPath, progressIndicator)
    GitHistoryUtils.loadDetails(project, root, commitConsumer, *parameters)
    changeProgressBarCalculating(progressIndicator)
    // TODO: add progress bar track
    BusFactorCalculation(context).computeAuthorship()
    return context
  }

  private fun setState(
    projectPath: String,
    branch: String,
    context: BusFactorComputationContext,
    after: () -> Unit = {}
  ) {
    ApplicationManager.getApplication().invokeLater {
      runWriteAction {
        state = MyState(projectPath, branch, context)
      }
      after()
      logger.info("Finish BF calculation")
    }
  }

  private fun GitCommit.date() = Date(this.timestamp)

  private fun afterDate(lastCommit: GitCommit) =
    Date.from(lastCommit.date().toInstant().minus(Duration.ofDays(BusFactorConstants.DAYS_GAP)))

  private fun changeProgressBarCalculating(progressIndicator: ProgressIndicator?) {
    progressIndicator?.let {
      it.text = RiskyPatternsBundle.message("calculating")
      it.fraction = MINING_FRACTION
    }
  }

  private fun countCommits(project: Project, root: VirtualFile, vararg parameters: String): CountCommitsConsumer {
    val consumer = CountCommitsConsumer()
    GitHistoryUtils.loadDetails(project, root, consumer, *parameters)
    return consumer
  }

  fun isValidFilePath(filePath: String): Boolean = runReadAction {
    Utils.isValidFilePath(filePath, state.context.configSnapshot.ignoreExtensions)
  }

  fun isValidFilePath(vFile: VirtualFile): Boolean = runReadAction {
    Utils.isValidFilePath(vFile, state.context.configSnapshot.ignoreExtensions)
  }

}
