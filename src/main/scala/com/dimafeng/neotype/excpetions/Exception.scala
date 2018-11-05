package com.dimafeng.neotype.excpetions

case class PropertyNotFoundException(message: String) extends Exception(message)
case class NoFieldsDefinedException(message: String) extends Exception(message)
