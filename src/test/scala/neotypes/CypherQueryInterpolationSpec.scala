package neotypes

import org.scalatest.FlatSpec

import neotypes.implicits.CypherString

class CypherQueryInterpolationSpec extends FlatSpec {
  it should "interpolation with one param" in {
    val name = "John"
    val query = c"create (a:Test {name: $name})"
    assert(query.rawQuery == "create (a:Test {name: $par1})")
    assert(query.params == Map("par1" -> "John"))
  }

  it should "interpolation with no params" in {
    assert(c"""create (a:Test {name: "test"})""".rawQuery == """create (a:Test {name: "test"})""")
  }

  it should "concat LazySessionBuilders" in {
    val name = "John"
    val born = 1980
    val query = c"""create (a:Test {name: $name,""" + c"born: $born}"
    assert(query.rawQuery == """create (a:Test {name: $par1, born: $par2})""")
    assert(query.params == Map("par1" -> "John", "par2" -> 1980))
  }
}
