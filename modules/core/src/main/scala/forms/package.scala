import models.Entity
import play.api.data.Form
import play.api.data.Forms.{nonEmptyText, seq, single}

import java.net.{MalformedURLException, URL}

/**
  * Form-related utilities
  */
package object forms {


  /**
    * Check if a string is a valid URL.
    */
  val isValidUrl: String => Boolean = s => try {
    new URL(s)
    true
  } catch {
    case _: MalformedURLException => false
  }

  import play.api.data.format.Formatter
  import play.api.data.{FormError, Forms, Mapping}
  import play.api.libs.json.Json

  /**
    * Constructs a simple mapping for a text field (mapped as `JsObject`)
    *
    * For example:
    * {{{
    *   Form("randomData" -> jsonObj(Status))
    * }}}
    */
  val entityForm: Mapping[Entity] = Forms.of(new Formatter[Entity] {
    def bind(key: String, data: Map[String, String]): Either[Seq[FormError], Entity] = {
      play.api.data.format.Formats.stringFormat.bind(key, data).flatMap { s =>
        scala.util.control.Exception.allCatch[Entity]
          .either(Json.parse(s).as[Entity](Entity.entityReads))
          .left.map(e => Seq(FormError(key, "error.jsonObj", Nil)))
      }
    }

    def unbind(key: String, value: Entity) = Map(key -> Json.stringify(Json.toJson(value)(Entity.entityWrites)))
  })

  /**
    * Form for a set of user or group identifiers that can
    * access a given resource.
    */
  val visibilityForm: Form[Seq[String]] = Form(single(
    services.data.Constants.ACCESSOR_PARAM -> seq(nonEmptyText)
  ))
}
