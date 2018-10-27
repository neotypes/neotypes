package com.dimafeng.neotype

import org.neo4j.driver.v1.{Record, Value}

trait ValueMarshallable[T] {
  def to(value: Value): T
}

trait RecordMarshallable[T] {
  def to(value: Record): T
}

object ValueMarshallable {

  implicit object StringValueMarshallable extends ValueMarshallable[String] {
    override def to(value: Value): String = value.asString()
  }

}

object RecordMarshallable {

  implicit object StringRecordMarshallable extends RecordMarshallable[String] {
    override def to(value: Record): String =
      implicitly[ValueMarshallable[String]].to(value.get(0)) //
  }

}