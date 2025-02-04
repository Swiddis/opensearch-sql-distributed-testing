# Dynamic Testing Primer

**Author:** [Simeon Widdis](https://github.com/Swiddis) <br> **Date:** January 31, 2025

Since starting to implement the [original RFC](https://github.com/opensearch-project/sql/issues/3220),
I think that there’s been some confusion about what exactly the dynamic testing framework is actually doing,
as I've assumed too much prerequisite knowledge.
I want to write this primer to introduce some ideas that I reference a lot, in as little space as possible.

This document tries to briefly explain, with examples:

1. Property-based testing
2. Context-sensitive Query Generation
3. Ternary Logic Partitioning

## What is Property-Based Testing?

**tl;dr:** Test by finding properties that apply to all valid inputs, instead of trying to specify input-output pairs.

I’m going to use the Python library [Hypothesis](https://hypothesis.readthedocs.io/en/latest/) as a reference here. While Python-specific, they have some of the best introductory documentation for these concepts that I’m aware of, so I’d recommend them for more information. The dynamic testing framework is built on [ScalaCheck](https://scalacheck.org/), which uses the same idea.

Normal unit tests follow an arrange-act-assert pattern:

1. Set up some data.
2. Perform some operations on the data.
3. Assert something about the result.

Property-based tests look more like this:

1. For all data matching some specification,
2. Perform some operations on the data.
3. Assert something about the result.

To keep the example as simple as possible, let’s write a Python sorting function with a blatantly obvious bug:

```py
def sort(ls: list[int]) -> list[int]:
    ls.sort()
    # Swap the first 2 elements if the first element is negative
    if ls[0] < 0:
        ls[0], ls[1] = ls[1], ls[0]
    return ls
```

Now we’re going to write a property-based test that makes sure our sort function works:

```py
from hypothesis import given
from hypothesis.strategies import lists, integers

@given(lists(integers()))
def test_sort_is_sorted(ls: list[int]):
    result = sort(ls)
    # Assert the list is sorted
    assert all(a <= b for a, b in zip(result, result[1:]))
```

Now let’s try running our test. Hypothesis immediately finds all three distinct bugs. For brevity, I’m only including the core of the output.

```
> pytest test_sorting.py
| ExceptionGroup: Hypothesis found 3 distinct failures. (3 sub-exceptions)
  +-+---------------- 1 ----------------
    | AssertionError: assert False
    | Falsifying example: test_sort_is_sorted(
    |     ls=[0, -1],
    | )
    +---------------- 2 ----------------
     | IndexError: list index out of range
    | Falsifying example: test_sort_is_sorted(
    |     ls=[-1],
    | )
    +---------------- 3 ----------------
    | IndexError: list index out of range
    | Falsifying example: test_sort_is_sorted(
    |     ls=[],
    | )
    +------------------------------------
```

The first bug is the one I intended when writing this example: sorting a list with negative numbers produces an incorrect result. The others are the framework correctly identifying two unintended issues that happen for short lists: you can’t check `ls[0]` if the list is empty, and you can’t swap it with `ls[1]` if it’s only of length 1.

That’s what property-based testing feels like: we have a property that we test (that `sort` produces a sorted list), and we define some inputs that it accepts (all possible lists of integers). The framework does the rest.

I want to take a moment to call out two things:

* Property-based tests are not a big list of generated test cases. This is *one* test that is run many times. The test report only contains one failing example for each distinct bug, not a report of every test that was run. (Indeed, they’re actually the shortest *possible* reproducers for each bug, due to a process called “shrinking.”)
* In this case, fixing the bugs is trivial. If, instead of fixing the bugs, we wanted to avoid the tests triggering the bugs in the first place, we would need to make the input generation code more complicated.[^1]

## What is Context-Sensitive Query Generation?

**tl;dr:** For query/data generation to be useful, we need to make an index, and only generate queries that apply to that index. The actual tests aren’t that complicated, the bulk of the work is data/query specification.

Here’s a brief rundown of how we run test cases for OpenSearch SQL (and equivalently PPL).

1. We generate an “index context,” which contains an index name, and a list of columns and their data types. We also generate sample data to populate that context, and finally create the actual index on OpenSearch. This is done in the `datagen` package.
2. We create a query generator, which is built using the index context as input. The query generator uses this to ensure the queries make some logical sense: it doesn’t generate columns which doesn’t exist, and it doesn’t try to e.g. use `+` for data types where addition makes no sense. This is all done in the `queries` package.
    1. The query generator more-or-less reimplements the SQL grammar but backwards: instead of trying to parse a string, we’re walking down the tree to find possible strings we can produce.
    2. “Toggling SQL features” in this context really just means adding or removing branches in the grammar. For example, disabling the `NOT` keyword [literally means commenting out the option](https://github.com/Swiddis/opensearch-sql-distributed-testing/blob/7dd24cd26b3363a14ff73adc040dfbe9f531d9d6/src/main/scala/queries/sql/ContextExprGen.scala#L54-L56).
    3. **“Context-sensitive” means we use the index metadata as part of query generation.**
3. We then supply the query generator as an input to a property. The property specifies how we run the query and assert something about the result. This is done in the `properties` package.
4. We then run the property itself, which samples the generator many times to produce random queries, and ensures the property holds. This happens in `Main`.

The process has a lot of preamble, but at the time of writing, similar to the Hypothesis example, *all of step 5 is just one test per language*. In the specific case, we expect to get a 200 result for valid queries. We then run that test a few hundred times.

If you want to add more query generation capabilities, check [Adding Query Features](./adding-query-features.md).

## What is Ternary Logic Partitioning?

**tl;dr:** It’s a simple property for testing queries, which is surprisingly effective at finding bugs. The details aren’t critical, but included here for completeness.

I use the term TLP a lot. It’s really just one example of a mathematical property that SQL queries must uphold[^2], the fact that I use it as an example doesn’t have much significance other than that it exists in the intersection between “easy to implement” and “finds lots of bugs.”

The idea is that each SQL query *q* can be split into 3 parts, for some boolean proposition *p*.

```
q WHERE p
q WHERE NOT p
q WHERE p IS NULL
```

Ternary Logic Partitioning says that, because there are only 3 possible results of any boolean proposition (true, false, or null), the result of running all 3 queries and combining them should give you the same results as just running *q* directly. If this doesn’t happen, there’s must be a bug in *p*.

As a concrete historic example, there was once a MySQL bug that did type conversion incorrectly when comparing constant floats with integer columns. If you had a table with a column `i INT` which had a single row with the value 0, you would get 0 results from all 3 of the given queries:

```
SELECT * FROM table WHERE 0.9 > i
SELECT * FROM table WHERE NOT 0.9 > i
SELECT * FROM table WHERE 0.9 > i IS NULL
```

In particular, the first query is supposed to match, as 0.9 > 0. This was considered a critical bug after it was reported to the MySQL team. The fact that the 3 partitions didn’t unify to the same result as `SELECT * FROM table` was the property failure that discovered the bug.

There are other ways to apply a very similar principle to more complicated queries like aggregations, WHERE is just an illustrative example and arguably the easiest to implement.

## Where did Distributed Testing go?

I renamed it to Dynamic Testing for clarity.

The repo still has the old name in the link to avoid breaking all the links, but ultimately I think that the concepts outlined above are more important than the part where we find tricks to change the number of run cycles from 10^2 to 10^6. At the time I thought scaling would be the hard part, but in practice we ended up finding bugs after 8 cycles, not thousands.

[^1]: In the specific case we can simply write `lists(integers(min_value=0), min_size=1)`, Hypothesis is a powerful library. For most real bugs this isn’t so simple — imagine for a more cleverly hidden sorting bug trying to write “generate only the lists that don’t trigger that bug.” Pretty much any implementation would end up excluding too many lists. (Even this trivial example accidentally excludes large negative lists with duplicate minimum values. Expressing this possibility with Hypothesis strategies is left as an exercise to the reader.)
[^2]: For PPL, we need to use quaternary logic, since there’s both `NULL` and `MISSING`. The same idea applies.
