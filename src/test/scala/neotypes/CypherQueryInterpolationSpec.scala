package neotypes

import org.scalatest.FlatSpec

import neotypes.implicits.CypherString

class CypherQueryInterpolationSpec extends FlatSpec {
  it should "interpolation with one param" in {
    val name = "John"
    val query = c"create (a:Test {name: $name})"
    assert(query.rawQuery == "create (a:Test {name: $p1})")
    assert(query.params == Map("p1" -> "John"))
  }

  it should "interpolation with no params" in {
    assert(c"""create (a:Test {name: "test"})""".rawQuery == """create (a:Test {name: "test"})""")
  }

  it should "concat DeferredQueryBuilders" in {
    val name = "John"
    val born = 1980
    val query = c"""create (a:Test {name: $name,""" + c"born: $born})"
    assert(query.rawQuery == """create (a:Test {name: $p1, born: $p2})""")
    assert(query.params == Map("p1" -> "John", "p2" -> 1980))

    val query2 = c"""create (a:Test {name: $name,""" + c"born: 1980})"
    assert(query2.rawQuery == """create (a:Test {name: $p1, born: 1980})""")
    assert(query2.params == Map("p1" -> "John"))

    val query3 = c"""create (a:Test {name: "John",""" + c"born: $born})"
    assert(query3.rawQuery == """create (a:Test {name: "John", born: $p1})""")
    assert(query3.params == Map("p1" -> 1980))
  }

  it should "concat DeferredQueryBuilder with String" in {
    val name = "John"
    val query = c"""create (a:Test {name: $name,""" + "born: 1980})"
    assert(query.rawQuery == """create (a:Test {name: $p1, born: 1980})""")
    assert(query.params == Map("p1" -> "John"))
  }
}
