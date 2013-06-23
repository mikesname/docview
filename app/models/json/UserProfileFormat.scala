package models.json

import play.api.libs.functional.syntax._
import play.api.libs.json._

import models._
import defines.EntityType
import defines.EnumUtils._
import models.base.Accessor


object UserProfileFormat {
  import models.UserProfileF._
  import models.Entity._

  implicit val userProfileWrites: Writes[UserProfileF] = new Writes[UserProfileF] {
    def writes(d: UserProfileF): JsValue = {
      Json.obj(
        ID -> d.id,
        TYPE -> d.isA,
        DATA -> Json.obj(
          IDENTIFIER -> d.identifier,
          NAME -> d.name,
          LOCATION -> d.location,
          ABOUT -> d.about,
          LANGUAGES -> d.languages
        )
      )
    }
  }

  implicit val userProfileReads: Reads[UserProfileF] = (
    (__ \ TYPE).read[EntityType.Value](equalsReads(EntityType.UserProfile)) and
    (__ \ ID).readNullable[String] and
      (__ \ DATA \ IDENTIFIER).read[String] and
      (__ \ DATA \ NAME).read[String] and
      (__ \ DATA \ LOCATION).readNullable[String] and
      (__ \ DATA \ ABOUT).readNullable[String] and
      (__ \ DATA \ LANGUAGES).readNullable[List[String]]
    )(UserProfileF.apply _)

  implicit val restFormat: Format[UserProfileF] = Format(userProfileReads,userProfileWrites)

  private implicit val groupReads = GroupFormat.metaReads

  implicit val metaReads: Reads[UserProfileMeta] = (
    __.read[JsObject] and
    __.read[UserProfileF] and
    (__ \ RELATIONSHIPS \ Accessor.BELONGS_REL).lazyReadNullable[List[GroupMeta]](
      Reads.list[GroupMeta]).map(_.getOrElse(List.empty[GroupMeta]))
  )(UserProfileMeta.apply _)
}