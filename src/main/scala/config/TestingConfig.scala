package config

import pureconfig.ConfigReader.Result
import pureconfig.{ConfigReader, ConfigSource}

case class TestingConfig(
                          /**
   * Whether we generate queries with expressions that evaluate to constants.
   *
   * see: https://github.com/opensearch-project/sql/issues/3266
   */
                          disableConstantExprs: Boolean
) derives ConfigReader

object Testing {
  val config: TestingConfig = ConfigSource.default.load[TestingConfig]
    .getOrElse(TestingConfig(disableConstantExprs = true))
}
