![Logo](neotypes.png)

# neotypes

Type-safe scala asynchronous driver for neo4j

## Usage

```scala
val s = new Session[Future](driver.session())
val r = s.transact[Seq[Person :: String :: HNil]](tx =>
  "MATCH (tom {name: \"Tom Hanks\"}) RETURN tom as t1, tom.name".query[Person :: String :: HNil]().list(tx)
)

r: Future[Seq[Person :: String :: HNil]]
```

## Release notes

TODO

## Publishing

TODO
