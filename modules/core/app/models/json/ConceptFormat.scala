package models.json

import play.api.libs.functional.syntax._
import play.api.libs.json._

import defines.EntityType
import models.base.Accessor
import models._
import eu.ehri.project.definitions.Ontology._


object ConceptFormat {
  import models.json.ConceptDescriptionFormat._
  import models.Entity._

  implicit val conceptWrites: Writes[ConceptF] = new Writes[ConceptF] {
    def writes(d: ConceptF): JsValue = {
      Json.obj(
        ID -> d.id,
        TYPE -> d.isA,
        DATA -> Json.obj(
          IDENTIFIER -> d.identifier
        ),
        RELATIONSHIPS -> Json.obj(
          DESCRIPTION_FOR_ENTITY -> Json.toJson(d.descriptions.map(Json.toJson(_)).toSeq)
        )
      )
    }
  }

  implicit val conceptReads: Reads[ConceptF] = (
    (__ \ TYPE).read[EntityType.Value](equalsReads(EntityType.Concept)) and
    (__ \ ID).readNullable[String] and
    (__ \ DATA \ IDENTIFIER).read[String] and
    ((__ \ RELATIONSHIPS \ DESCRIPTION_FOR_ENTITY).lazyReadNullable[List[ConceptDescriptionF]](
        Reads.list[ConceptDescriptionF]).map(_.getOrElse(List.empty[ConceptDescriptionF])))
    )(ConceptF.apply _)

  implicit val restFormat: Format[ConceptF] = Format(conceptReads,conceptWrites)

  private implicit val systemEventReads = SystemEventFormat.metaReads
  private implicit val vocabularyReads = VocabularyFormat.metaReads

  implicit val metaReads: Reads[Concept] = (
    __.read[ConceptF] and
    (__ \ RELATIONSHIPS \ ITEM_IN_AUTHORITATIVE_SET).lazyReadNullable[List[Vocabulary]](
      Reads.list[Vocabulary]).map(_.flatMap(_.headOption)) and
    (__ \ RELATIONSHIPS \ CONCEPT_HAS_BROADER).lazyReadNullable[List[Concept]](
      Reads.list[Concept]).map(_.flatMap(_.headOption)) and
    (__ \ RELATIONSHIPS \ CONCEPT_HAS_BROADER).lazyReadNullable[List[Concept]](
      Reads.list[Concept]).map(_.getOrElse(List.empty[Concept])) and
    (__ \ RELATIONSHIPS \ IS_ACCESSIBLE_TO).lazyReadNullable[List[Accessor]](
        Reads.list(Accessor.Converter.restReads)).map(_.getOrElse(List.empty[Accessor])) and
    (__ \ RELATIONSHIPS \ ENTITY_HAS_LIFECYCLE_EVENT).lazyReadNullable[List[SystemEvent]](
      Reads.list[SystemEvent]).map(_.flatMap(_.headOption)) and
    (__ \ META).readNullable[JsObject].map(_.getOrElse(JsObject(Seq())))
  )(Concept.apply _)
}
