import sbt.Keys._
import sbt._
import sbt.util.CacheStoreFactory

object Boilerplate {
  private final val letters: List[Char] =
    List.range(start = 'b', end = 'z')

  private def generateMethod(generateFun: (Int, List[Char], String) => List[String]): List[String] =
    List.range(
      start = 2,
      end = 23 // Actually 22, but range is exclusive on end.
    ).flatMap { n =>
      val parameters = letters.take(n)
      val typeParameters = parameters.map(_.toUpper).mkString(", ")
      generateFun(n, parameters, typeParameters)
    }

  private def generateBoilerplateResultMappers(): List[String] = {
    def generateAnd(n: Int, parameters: List[Char], typeParameters: String): List[String] =
      "/** Combines the given [[ResultMapper]s into a single tupled one, merges errors. */" ::
      s"final def and[${typeParameters}](" ::
      parameters.map(l => s"${l}: ResultMapper[${l.toUpper}]").mkString(", ") ::
      s"): ResultMapper[(${typeParameters})] =" ::
      s"(${parameters.mkString(" and ")}).map {" ::
      s"case ${"(" * n}${parameters.mkString("), ")}) =>" ::
      parameters.mkString("(", ",", ")") ::
      "}" ::
      Nil

    def generateCombine(n: Int, parameters: List[Char], typeParameters: String): List[String] =
      "/** Combines the given [[ResultMapper]s into a single one using the provided function, merges errors. */" ::
      s"final def combine[A, ${typeParameters}](" ::
      parameters.map(l => s"${l}: ResultMapper[${l.toUpper}]").mkString(", ") ::
      s")(fun: (${typeParameters}) => A): ResultMapper[A] =" ::
      s"(${parameters.mkString(" and ")}).map {" ::
      s"case ${"(" * n}${parameters.mkString("), ")}) =>" ::
      parameters.mkString("fun(", ",", ")") ::
      "}" ::
      Nil

    def generateFromFunction(n: Int, parameters: List[Char], typeParameters: String): List[String] =
      "/** Creates a [[ResultMapper]] from the given function, based on a heterogenous list. */" ::
      s"final def fromFunction[A, ${typeParameters}]" ::
      s"(fun: (${typeParameters}) => A)" ::
      "(implicit " ::
      parameters.map(l => s"${l}: ResultMapper[${l.toUpper}]").mkString(", ") ::
      "): ResultMapper[A] =" ::
      "values.map(_.toList).emap {" ::
      s"case ${parameters.map(l => s"v${l}").mkString(" :: ")} :: _ =>" ::
      s"(${parameters.map(l => s"${l}.decode(v${l})").mkString(" and ")}).map {" ::
      s"case ${"(" * n}${parameters.mkString("), ")}) =>" ::
      parameters.mkString("fun(", ",", ")") ::
      "}" ::
      "case values =>" ::
      "Left(IncoercibleException(message = s\"Wrong number of arguments: ${values}\"))" ::
      "}" ::
      Nil

    def generateFromFunctionNamed(n: Int, parameters: List[Char], typeParameters: String): List[String] =
      "/** Creates a [[ResultMapper]] from the given function, based on a [[NeoObject]]. */" ::
      s"final def fromFunctionNamed[A, ${typeParameters}](" ::
      parameters.map(l => s"n${l}: String").mkString(", ") ::
      s")(fun: (${typeParameters}) => A)" ::
      "(implicit " ::
      parameters.map(l => s"${l}: ResultMapper[${l.toUpper}]").mkString(", ") ::
      "): ResultMapper[A] =" ::
      "neoObject.emap { obj =>" ::
      s"(${parameters.map(l => s"obj.getAs(key = n${l})(${l})").mkString(" and ")}).map {" ::
      s"case ${"(" * n}${parameters.mkString("), ")}) =>" ::
      parameters.mkString("fun(", ",", ")") ::
      "}" ::
      "}" ::
      Nil

    def generateTuple(n: Int, parameters: List[Char], typeParameters: String): List[String] =
      "/** Creates a tuple [[ResultMapper]], based on a heterogenous list. */" ::
      s"implicit final def tuple[${typeParameters}](implicit" ::
      parameters.map(l => s"${l}: ResultMapper[${l.toUpper}]").mkString(", ") ::
      s"): ResultMapper[(${typeParameters})] =" ::
      "values.map(_.toList).emap {" ::
      s"case ${parameters.map(l => s"v${l}").mkString(" :: ")} :: _ =>" ::
      s"(${parameters.map(l => s"${l}.decode(v${l})").mkString(" and ")}).map {" ::
      s"case ${"(" * n}${parameters.mkString("), ")}) =>" ::
      parameters.mkString("(", ",", ")") ::
      "}" ::
      "case values =>" ::
      "Left(IncoercibleException(message = s\"Wrong number of arguments: ${values}\"))" ::
      "}" ::
      Nil

    def generateTupleNamed(n: Int, parameters: List[Char], typeParameters: String): List[String] =
      "/** Creates a tuple [[ResultMapper]], based on a [[NeoObject]]. */" ::
      s"final def tupleNamed[${typeParameters}](" ::
      parameters.map(l => s"n${l}: String").mkString(", ") ::
      ")(implicit " ::
      parameters.map(l => s"${l}: ResultMapper[${l.toUpper}]").mkString(", ") ::
      s"): ResultMapper[(${typeParameters})] =" ::
      "neoObject.emap { obj =>" ::
      s"(${parameters.map(l => s"obj.getAs(key = n${l})(${l})").mkString(" and ")}).map {" ::
      s"case ${"(" * n}${parameters.mkString("), ")}) =>" ::
      parameters.mkString("(", ",", ")") ::
      "}" ::
      "}" ::
      Nil

    def generateProduct(n: Int, parameters: List[Char], typeParameters: String): List[String] =
      "/** Creates a [[ResultMapper]] for a product, based on a heterogenous list. */" ::
      s"final def product[A <: Product, ${typeParameters}]" ::
      parameters.map(l => s"${l}: ResultMapper[${l.toUpper}]").mkString("(", ", ", ")") ::
      s"(fun: (${typeParameters}) => A): ResultMapper[A] =" ::
      s"fromFunction(fun)(${parameters.mkString(", ")})" ::
      Nil

    def generateProductNamed(n: Int, parameters: List[Char], typeParameters: String): List[String] =
      "/** Creates a [[ResultMapper]] for a product, based on a [[NeoObject]]. */" ::
      s"final def productNamed[A <: Product, ${typeParameters}]" ::
      parameters.map(l => s"${l}: (String, ResultMapper[${l.toUpper}])").mkString("(", ", ", ")") ::
      s"(fun: (${typeParameters}) => A): ResultMapper[A] =" ::
      "fromFunctionNamed(" ::
      parameters.map(l => s"${l}._1").mkString(", ") ::
      ")(fun)(" ::
      parameters.map(l => s"${l}._2").mkString(", ") ::
      ")" ::
      Nil

    "package neotypes" ::
    "package boilerplate" ::
    "import internal.syntax.either._" ::
    "import mappers.ResultMapper" ::
    "import model.exceptions.IncoercibleException" ::
    "private[neotypes] trait BoilerplateResultMappers { self: ResultMapper.type =>" ::
    generateMethod(generateFun = generateAnd) :::
    generateMethod(generateFun = generateCombine) :::
    generateMethod(generateFun = generateFromFunction) :::
    generateMethod(generateFun = generateFromFunctionNamed) :::
    generateMethod(generateFun = generateTuple) :::
    generateMethod(generateFun = generateTupleNamed) :::
    generateMethod(generateFun = generateProduct) :::
    generateMethod(generateFun = generateProductNamed) :::
    "}" ::
    Nil
  }

  val generatorTask = Def.task {
    val log = streams.value.log
    val cachedFun =
      FileFunction.cached(
        cacheBaseDirectory = streams.value.cacheDirectory / "boilerplate",
        inStyle = FilesInfo.exists,
        outStyle = FilesInfo.exists
      ) { _ =>
        log.info("Generating boilerplate files")

        // Save the boilerplate file to the managed sources dir.
        val file = (Compile / sourceManaged).value / "boilerplate" / "BoilerplateResultMappers.scala"
        IO.writeLines(file, lines = generateBoilerplateResultMappers())
        Set(file)
      }

    cachedFun(Set(file("project/Boilerplate.scala"))).toSeq
  }
}
