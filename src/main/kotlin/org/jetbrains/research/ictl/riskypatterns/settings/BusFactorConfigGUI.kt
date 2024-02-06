package org.jetbrains.research.ictl.riskypatterns.settings

import com.intellij.openapi.fileChooser.FileChooser
import com.intellij.openapi.fileChooser.FileChooserDescriptor
import java.text.DecimalFormat
import java.text.NumberFormat
import javax.swing.*
import javax.swing.text.DefaultFormatterFactory
import javax.swing.text.NumberFormatter


class BusFactorConfigGUI {

  companion object {
    fun getJTextDoubleValue(field: JTextField): Double {
      val text = field.text
      return if (text.isEmpty()) {
        -1.0
      } else text.toDouble()
    }

    fun setJTextDoubleValue(field: JTextField, value: Double) {
      field.text = value.toString()
    }
  }

  lateinit var rootPanel: JPanel
    private set

  private lateinit var abandonmentPartitionThresholdField: JFormattedTextField
  var abandonmentPartitionThreshold: Double
    get() = getJTextDoubleValue(abandonmentPartitionThresholdField)
    set(value) = setJTextDoubleValue(abandonmentPartitionThresholdField, value)

  private lateinit var minorContributorsThresholdField: JFormattedTextField
  var minorContributorsThreshold: Double
    get() = getJTextDoubleValue(minorContributorsThresholdField)
    set(value) = setJTextDoubleValue(minorContributorsThresholdField, value)

  private lateinit var useReviewersCheckBox: JCheckBox
  var useReviewers: Boolean
    get() = useReviewersCheckBox.isSelected
    set(value) {
      useReviewersCheckBox.isSelected = value
    }

  private lateinit var ignoredExtensionsField: JFormattedTextField
  var ignoredExtensions: Set<String>
    get() =
      ignoredExtensionsField.text.replace(Regex("\\s+"), " ").split(" ").toSet()
    set(value) {
      ignoredExtensionsField.text = value.joinToString(" ")
    }

  init {
    val nf: NumberFormat = DecimalFormat.getInstance()
    nf.maximumFractionDigits = 3
    nf.minimumFractionDigits = 1

    nf.maximumIntegerDigits = 1
    nf.minimumFractionDigits = 1

    val nff = NumberFormatter(nf)
    nff.minimum = 0.0
    nff.maximum = 1.0
    nff.allowsInvalid = false
    val factory = DefaultFormatterFactory(nff)

    abandonmentPartitionThresholdField.formatterFactory = factory
    minorContributorsThresholdField.formatterFactory = factory
  }
}
