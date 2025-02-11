# Non-optimizing Reference Engine Construction (NoREC)

**Author:** [Simeon Widdis](https://github.com/Swiddis) <br>
**Date:** February 10, 2025 <br>
**Prerequisites:** [Dynamic Testing Primer](./primer.md)

NoREC is another fun trick for testing database correctness, particularly with respect to an
optimizer. We want that a database's optimized query is equivalent to an equivalent non-optimized
version. The [original preprint by Manuel Rigger and Zhendong Su](https://arxiv.org/abs/2007.08292)
is a well-written resource.

## Overview

The core idea is that, since we can't simply toggle optimization in OpenSearch, we want to instead
rewrite our queries to dodge optimization. An illustrative example (from the paper) is to rewrite
WHERE clauses:

For a query like this which may be optimized by e.g. using a sorted index,

```sql
SELECT x FROM example WHERE x > 0
```

We can instead run this query, which can't as easily be optimized:

```sql
SELECT (x > 0) FROM example
```

Then we can verify that the number of `TRUE` results in the second query is the same as the number
of results from the first query.
