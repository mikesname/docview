package models


trait ModelData {
  def id: Option[String]

  def isA: EntityType.Value
}

