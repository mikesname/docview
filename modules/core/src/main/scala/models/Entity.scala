package models

import play.api.libs.functional.syntax._
import play.api.libs.json._
import utils.EnumUtils


object Entity {

  val ID = "id"
  val IDENTIFIER = "identifier"
  val ISA = "isA"
  val DATA = "data"
  val TYPE = "type"
  val RELATIONSHIPS = "relationships"
  val META = "meta"
  val CHILD_COUNT = "childCount"

  /**
   * Reads a generic entity.
   */
  implicit val entityReads: Reads[Entity] = (
    (__ \ ID).read[String] and
    (__ \ TYPE).read[EntityType.Type](EnumUtils.enumReads(EntityType)) and
    (__ \ DATA).lazyRead(Reads.map[JsValue]) and
    (__ \ RELATIONSHIPS).lazyRead(Reads.map[Seq[Entity]](Reads.seq(entityReads)))
  )(Entity.apply _)

  /**
   * Writes a generic entity.
   */
  implicit val entityWrites: Writes[Entity] = (
    (__ \ ID).write[String] and
    (__ \ TYPE).write[EntityType.Type] and
    (__ \ DATA).lazyWrite(Writes.map[JsValue]) and
    (__ \ RELATIONSHIPS).lazyWrite(Writes.map[Seq[Entity]])
  )(unlift(Entity.unapply))

  /**
   * Format for a generic entity.
   */
  val entityFormat: Format[Entity] = Format(entityReads, entityWrites)
}

case class Entity(
  id: String,
  `type`: EntityType.Value,
  data: Map[String, JsValue] = Map(),
  relationships: Map[String, Seq[Entity]] = Map()) {

  lazy val isA: EntityType.Value = `type`
}
