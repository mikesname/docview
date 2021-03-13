package models

import play.api.i18n.Messages


trait Aliased extends Model {
  def allNames(implicit messages: Messages): Seq[String] = Seq(toStringLang(messages))
}
