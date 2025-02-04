# Adding Query Features

**Date:** February 3, 2025

This document covers how to enhance query generation, such as by adding more types of queries or
adding new operations.

## Overview

In the `queries` module, you'll find packages for every supported language alongside general-purpose
traits. For the purposes of this document, we'll use `sql` as an example. In a file called `Root`,
you'll find the root object for query generation, which has generators for top-level query types. In
the case of `sql`, this is a `SelectQueryGenerator`, which can assemble (potentially many) shapes of
queries. These shapes may rely on different sub-generators.

To support query generation, you need two things: serialization logic, and a generator.
- Serialization logic is done by implementing the `QuerySerializable` trait, for the most part we
  supply this with separated `case class`es which serialize with templates. Example:
  [`queries.sql.Expr`](../src/main/scala/queries/sql/Expr.scala).
- Generators are instances of `org.scalacheck.Gen` which use a `QueryContext` to for type-safety.
  The [ScalaCheck user guide](https://github.com/typelevel/scalacheck/blob/main/doc/UserGuide.md)
  has a lot of information on how to build generators. Example:
  [`queries.sql.ContextExprGen`](../src/main/scala/queries/sql/ContextExprGen.scala).

## Query Shapes

Creating a new query shape is worthwhile when a feature doesn't uphold the same properties as the
existing shape. As an example, for [TLP](./primer.md), these are three shapes:

```
SELECT <columns> FROM <tables> WHERE p
SELECT DISTINCT <columns> FROM <tables> WHERE p
SELECT MIN(<column>) FROM <tables> WHERE p
```

The reason is that, when doing TLP unification, we can't use the same approach for all of them.
- For the first query we can do a multiset-union and ensure we get the same result back.
- For the second query, the DISTINCT clause means that values which show up in multiple partitions
  are dropped. We need to revert to a regular set-union.
- For the third query, there will be one result per partition. The best we can do to unify is to
  take the minimum between the three partitions.

Supporting generation of more query shapes should be done by adding more generator-constructors to
the `Root` generator. Depending on the exact situation, it's likely possible to reuse an existing
generator and replace any non-applicable parts.

## Typed Expressions

Query shapes are some of the story, but arguably the bulk of the features are for expressions.

For results to make sense, we want to generate strongly-typed expressions. For instance, an earlier
version of the SQL generator once yielded `SELECT * FROM test WHERE 1 - FALSE`.

The mechanism here is easy to show but not necessarily easy to explain. We use generics to track
expression types. Consider these `sql.Expr` cases and their associated generators:

```scala 3
case class Literal[T](value: T) extends Expr[T]:
  override def serialize(): String =
    value.toString.toUpperCase

case class BinaryOp[A, B](left: Expr[A], op: String, right: Expr[A]) extends Expr[B]:
  override def serialize(): String =
    op.replace("$1", left.serialize()).replace("$2", right.serialize())

object ExprGen {
  def literal[T](gen: Gen[T]): Gen[Expr[T]] = gen.map(Literal(_))

  def binaryOp[A, B](ops: Seq[String], argGen: Gen[Expr[A]]): Gen[Expr[B]] =
    for {
      left <- argGen
      op <- Gen.oneOf(ops)
      right <- argGen
    } yield BinaryOp(left, op, right)
}
```

How here's a simplified version of our boolean expression generator:

```scala 3
def intExpr(context: IndexContext): Gen[Expr[Int]] =
  ExprGen.literal(Gen.choose(-1000, 1000))

def boolExpr(context: IndexContext): Gen[Expr[SqlBoolean]] = {
  // For demo purposes, a boolean expression is either a literal, or an integer comparison
  Gen.oneOf(
    ExprGen.literal(Gen.oneOf(false, true, SqlNull())),
    ExprGen.binaryOp(
      List(
        "$1 = $2",
        "$1 <> $2",
        "$1 > $2",
        "$1 < $2",
        "$1 >= $2",
        "$1 <= $2"
      ),
      intExpr(context)
    )
  )
}
```

If you wanted to add more ways to generate booleans from pairs to integers, you would add the
appropriate templates to the list. If you wanted other input types, you could add them with new
cases in the top-level `oneOf`. (If an expression supports mixed-type inputs, scala supports
multi-types with `|`: `Int | Float`.) If you want other output types, it would be another
`${type}Expr` method.

In practice, we also have a depth field to support nested expression generation.
