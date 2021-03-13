package models


trait Temporal {
  def dates: Seq[DatePeriodF]
  def dateRange: String = dates.map(_.years).mkString(", ")
}
