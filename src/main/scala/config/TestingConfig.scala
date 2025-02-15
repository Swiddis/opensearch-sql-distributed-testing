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

/** Makes an educated guess on a good number of threads to use for property
  * checking. A decent handful of threads per processor since we're bound by
  * blocking I/O.
  *
  * In a perfect world ScalaCheck would support async task execution and not
  * need this, but the work of implementing that sort of runner is unlikely to
  * be significantly better spent than just using a worker number that's "good
  * enough".
  *
  * @return
  *   The number of workers to use per property check run.
  */
def workerCount(): Int = {
  val workers = Testing.config.concurrency match {
    case ConcurrencyLevel.None => 1
    case ConcurrencyLevel.Low  => Runtime.getRuntime.availableProcessors
    case ConcurrencyLevel.High => 32 * Runtime.getRuntime.availableProcessors
  }
  Math.min(
    workers,
    1000
  ) // Upper bound: default max OpenSearch search queue size
}
