package models

import play.api.i18n.Messages


trait DescribedModel extends Model {

  type T <: Described

  def descriptions: Seq[T#D] = data.descriptions

  override def toStringLang(implicit messages: Messages): String =
    data.primaryDescription(messages).orElse(descriptions.headOption).fold(id)(_.name)

  private lazy val allAccessPoints = descriptions.flatMap(_.accessPoints)

  /**
    * Links that relate to access points on this item's description(s)
    */
  def accessPointLinks(links: Seq[Link]): Seq[(Link,AccessPointF)] = for {
    link <- links.filterNot(_.bodies.isEmpty)
    accessPoint <- allAccessPoints.find(a => link.bodies.map(_.data.id).contains(a.id))
  } yield (link, accessPoint)

  /**
    * Links that related to access points, ordered by access point type.
    */
  def accessPointLinksByType(links: Seq[Link]): Map[AccessPointF.AccessPointType.Value, Seq[(Link, AccessPointF)]] =
    accessPointLinks(links).groupBy(_._2.accessPointType)

  /**
    * Links that point to this item from other item's access points.
    */
  def externalLinks(links: Seq[Link]): Seq[Link] = for {
    link <- links.filter(_.bodies.nonEmpty)
    if link.bodies.map(_.data.id).intersect(allAccessPoints.map(_.id)).isEmpty
  } yield link

  /**
    * Links that don't relate to access points at all, such
    * as annotations that assert a relationship between two
    * items without "belonging" to either one.
    */
  def annotationLinks(links: Seq[Link]): Seq[Link] =
    links.filter(link => link.bodies.isEmpty && link.opposingTarget(this).isDefined)
}

object DescribedModel {
  val DESCRIPTIONS = "descriptions"
}
