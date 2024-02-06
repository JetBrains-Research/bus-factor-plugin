package org.jetbrains.research.ictl.riskypatterns.services.converters

import com.intellij.openapi.diagnostic.logger
import com.intellij.util.xmlb.Converter
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.jetbrains.research.ictl.riskypatterns.calculation.BusFactorComputationContext

class BusFactorConverter : Converter<BusFactorComputationContext>() {

  companion object {
    private val logger = logger<BusFactorConverter>()
  }

  private val json = Json { allowSpecialFloatingPointValues = true }

  override fun fromString(value: String): BusFactorComputationContext {
    return try {
      json.decodeFromString(value)
    } catch (e: Exception) {
      logger.info("Got exception while decoding state. ${e.message}; ${e.stackTrace}")
      BusFactorComputationContext()
    }
  }

  override fun toString(value: BusFactorComputationContext): String {
    return try {
      json.encodeToString(value)
    } catch (e: Exception) {
      logger.info("Got exception while encoding state. ${e.message}; ${e.stackTrace}")
      ""
    }
  }
}