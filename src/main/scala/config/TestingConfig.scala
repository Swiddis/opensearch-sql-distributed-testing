package config

import pureconfig.ConfigReader.Result
import pureconfig.generic.derivation.EnumConfigReader
import pureconfig.{ConfigReader, ConfigSource}

enum ConcurrencyLevel derives EnumConfigReader:
  case None, Low, High

case class TestingConfig(
    /** Whether we generate queries with expressions that evaluate to constants.
      *
      * @see
      *   https://github.com/opensearch-project/sql/issues/3266
      */
    disableConstantExprs: Boolean,

    /** Roughly how many worker threads we want to spawn for checking
      * properties. A low worker count will take a long time to run, but a high
      * worker count can cause resource exhaustion if the machine can't handle
      * it. Try changing this if you're falling too far to either side of that
      * spectrum.
      */
    concurrency: ConcurrencyLevel
) derives ConfigReader

object Testing {
  val config: TestingConfig = ConfigSource.default
    .load[TestingConfig]
    .getOrElse(
      TestingConfig(
        disableConstantExprs = true,
        concurrency = ConcurrencyLevel.Low
      )
    )
}
