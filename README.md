# OpenSearch Distributed Testing Framework

> [!IMPORTANT]
> Not (yet) an official OpenSearch repository. This is an early work in progress implementation.

The Distributed Testing Framework (DistTest) is a system for running scaled property-based tests
for the OpenSearch project.

## Introduction

Property-Based Testing is a testing methodology where instead of writing specific discrete tests
cases, one defines *properties* that code is expected to always uphold. A property can be as simple
as "the system doesn't crash," but could also analyze behaviors like "sorted queries must return
sorted results." Once properties are defined, the framework generates a large amount of test cases,
and ensures the system maintains these properties under all cases.

Or more succinctly described by David Maclver, the author of
[Hypothesis](https://hypothesis.works/articles/what-is-property-based-testing/):
> Property based testing is the construction of tests such that, when these tests are fuzzed,
> failures in the test reveal problems with the system under test that could not have been revealed
> by direct fuzzing of that system.

At the time of writing, the emphasis of the project is on the OpenSearch SQL plugin. See some of the
original context and motivation in the [related RFC](https://github.com/opensearch-project/sql/issues/3220).
The current implementation is inspired heavily by [SQLancer](https://github.com/sqlancer/sqlancer).

### Module Breakdown

In addition to `src/main/scala/Main` there are 4 packages:
- **config** defines the configuration file found in `src/main/resources`, it should hopefully be
  self-explanatory.
- **datagen** is about generating test data. See especially `Context.scala` which defines the core
  data model for generating context-sensitive test queries.
- **properties** holds all the properties under test, which are loaded and batched in `Main`.
- **queries** includes logic for generating SQL and PPL queries that respect, producing ScalaCheck
  Generators for an index set.

## Usage

The exact versioned language dependencies are listed in [mise.toml](./mise.toml), and can be
installed via [Mise](https://mise.jdx.dev/) with `mise install`. If you'd rather install them
manually, you really just need a copy of Scala 3, SBT, and a version 21 JDK.

This is a normal SBT project. You can compile code with `sbt compile`, and run it with `sbt run`. To
run the tests, you will need a running OpenSearch cluster on port 9200 with HTTP. The simplest way
is with [the Docker image](https://opensearch.org/docs/latest/install-and-configure/install-opensearch/docker/)
in a single-node configuration.

### Testing

The project is the tests. It's not clear yet what the failure modes of the project itself are.

## Contributing

See the [OpenSearch Contribution Guide](https://github.com/opensearch-project/.github/blob/main/CONTRIBUTING.md)
and the [OpenSearch Code of Conduct](https://github.com/opensearch-project/.github/blob/main/CODE_OF_CONDUCT.md) .

## License

This project is licensed under the [Apache v2.0 License](./LICENSE).
