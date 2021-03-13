package models

import play.api.data.Form
import play.api.data.Forms.{boolean, default, mapping, nonEmptyText}

case class DeleteChildrenOptions(all: Boolean, confirm: String, answer: String)

object DeleteChildrenOptions {
  val ALL = "all"
  val CONFIRM = "confirm"
  val ANSWER = "answer"

  val form: Form[DeleteChildrenOptions]  = Form(
    mapping(
      ALL -> default(boolean, false),
      CONFIRM -> nonEmptyText(minLength = 3),
      ANSWER -> nonEmptyText
    )(DeleteChildrenOptions.apply)(DeleteChildrenOptions.unapply)
      .verifying("item.deleteChildren.badConfirmation", data => data.confirm.equalsIgnoreCase(data.answer))
  )
}
