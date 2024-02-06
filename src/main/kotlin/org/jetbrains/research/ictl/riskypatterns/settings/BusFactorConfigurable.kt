package org.jetbrains.research.ictl.riskypatterns.settings

import com.intellij.openapi.options.SearchableConfigurable
import javax.swing.JComponent
import kotlin.properties.Delegates

class BusFactorConfigurable : SearchableConfigurable {

  private var gui by Delegates.notNull<BusFactorConfigGUI>()

  override fun getDisplayName(): String = "BusFactor Settings"

  override fun getId(): String = "preferences.BusFactor"

  override fun getPreferredFocusedComponent(): JComponent = gui.rootPanel


  override fun createComponent(): JComponent {
    gui = BusFactorConfigGUI()
    return gui.rootPanel
  }

  override fun isModified(): Boolean {
    val settings = BusFactorConfigService.instance

    return (settings.minorContributorsThreshold != gui.minorContributorsThreshold) ||
        (settings.abandonmentPartitionThreshold != gui.abandonmentPartitionThreshold) ||
        (settings.useReviewers != gui.useReviewers) ||
        (settings.ignoreExtensions != gui.ignoredExtensions)
  }

  override fun apply() {
    val settings = BusFactorConfigService.instance
    settings.abandonmentPartitionThreshold = gui.abandonmentPartitionThreshold
    settings.minorContributorsThreshold = gui.minorContributorsThreshold
    settings.useReviewers = gui.useReviewers
    settings.ignoreExtensions = gui.ignoredExtensions
  }

  override fun reset() {
    val settings = BusFactorConfigService.instance
    gui.abandonmentPartitionThreshold = settings.abandonmentPartitionThreshold
    gui.minorContributorsThreshold = settings.minorContributorsThreshold
    gui.useReviewers = settings.useReviewers
    gui.ignoredExtensions = settings.ignoreExtensions
  }

}