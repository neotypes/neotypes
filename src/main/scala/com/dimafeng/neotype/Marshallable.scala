package com.dimafeng.neotype

import org.neo4j.driver.v1.Value

trait ValueMarshallable[T] {
  def to(fieldName: String, value: Option[Value]): Either[Throwable, T]
}

trait RecordMarshallable[T] {
  def to(value: Seq[(String, Value)]): Either[Throwable, T]
}