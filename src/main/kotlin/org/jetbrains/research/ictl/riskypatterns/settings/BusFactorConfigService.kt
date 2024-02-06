package org.jetbrains.research.ictl.riskypatterns.settings

import com.intellij.openapi.components.*
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.xmlb.XmlSerializerUtil
import kotlinx.serialization.Serializable
import org.jetbrains.research.ictl.riskypatterns.Utils
import org.jetbrains.research.ictl.riskypatterns.calculation.BusFactorConfigSnapshot

@State(name = "BusFactorConfig", storages = [(Storage("BusFactorConfig.xml"))])
@Service(Service.Level.PROJECT)
class BusFactorConfigService : PersistentStateComponent<BusFactorConfigService> {

  companion object {
    val instance: BusFactorConfigService
      get() = service()
  }

  @Suppress("PROVIDED_RUNTIME_TOO_LOW")
  @Serializable
  data class ConfigSnapshot(
    val busFactorConfigSnapshot: BusFactorConfigSnapshot,
    val minorContributorsThreshold: Double,
    val abandonmentPartitionThreshold: Double
  )

  var minorContributorsThreshold = 0.5
  var abandonmentPartitionThreshold = 0.5
  var useReviewers: Boolean = false
  var weightedAuthorship = true
  var ignoreExtensions: Set<String> = setOf(
    "txt",
    "xml",
    "json",
    "png",
    "jpeg",
    "svg",
    "yaml",
    "yml",
    "cfg"
  )

  override fun getState(): BusFactorConfigService = this

  override fun loadState(state: BusFactorConfigService) = XmlSerializerUtil.copyBean(state, this)

  fun snapshot() = ConfigSnapshot(
    BusFactorConfigSnapshot(
      useReviewers, weightedAuthorship, ignoreExtensions
    ),
    minorContributorsThreshold, abandonmentPartitionThreshold
  )

  fun isValidFilePath(filePath: String): Boolean = Utils.isValidFilePath(filePath, ignoreExtensions)

  fun isValidFilePath(vFile: VirtualFile): Boolean = Utils.isValidFilePath(vFile, ignoreExtensions)

}