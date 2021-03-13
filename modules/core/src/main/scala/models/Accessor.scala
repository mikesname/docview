package models

import play.api.libs.json._


object Accessor {
  final val ADMIN_GROUP_NAME = "admin"
  final val BELONGS_REL = "belongsTo"

  implicit object Converter extends Readable[Accessor] {
    implicit val restReads: Reads[Accessor] = Reads[Accessor](
      _.validate[Accessor](Model.Converter.restReads.asInstanceOf[Reads[Accessor]]))
  }

  /**
   * This function allows getting a dynamic Resource for an Accessor given
   * the entity type.
   */
  def resourceFor(t: EntityType.Value): ContentType[Accessor] = new ContentType[Accessor] {
    val restReads: Reads[Accessor] = Converter.restReads
    def entityType: EntityType.Value = t
    def contentType: ContentTypes.Value = ContentTypes.withName(t.toString)
  }
}

trait Accessor extends Model {
  def groups: Seq[Group]
  def id: String
  def isA: EntityType.Value

  lazy val allGroups: Seq[Group] = getGroups(this)

  def isAdmin: Boolean = getAccessor(groups, Accessor.ADMIN_GROUP_NAME).isDefined

  // Search up the tree(?) if parent groups, looking
  // for one with the desired id.
  def getAccessor(groups: Seq[Accessor], id: String): Option[Accessor] = {
    groups.toList match {
      case lst@head :: rest =>
        if (head.id == id) Some(head)
        else getAccessor(head.groups, id) match {
          case s@Some(g) => s
          case None => getAccessor(rest, id)
        }
      case Nil => None
    }
  }

  private def getGroups(acc: Accessor): Seq[Group] = {
    acc.groups.foldLeft(acc.groups) { case (all, g) =>
      all ++ getGroups(g)
    }.distinct
  }

  /**
   * Get the permission grant for a given permission (if any), which contains
   * the accessor to whom the permission was granted.
   *
   * @param globalPerms a global permission set
   * @param contentType a content type
   * @param permission a permission type
   */
  def getPermission(globalPerms: GlobalPermissionSet, contentType: ContentTypes.Value, permission: PermissionType.Value): Option[Permission] = {
    val accessors = globalPerms.data.flatMap {
      case (u, perms) =>
        perms.get(contentType).flatMap { permSet =>
          if (permSet.exists(p => PermissionType.in(p, permission))) Some((u, permission))
          else None
        }
    }
    // If we have one or more accessors, then we are going to
    // grant the permission, but we need to search the list
    // to determine the accessor who was granted the permissions.
    accessors.headOption.map { case (userId, perm) =>
      if (id == userId) Permission(perm)
      else {
        getAccessor(groups, userId) match {
          case Some(u) if u.id == id => Permission(perm)
          case Some(u) => Permission(perm, Some(u.id))
          case x => Permission(perm)
        }
      }
    }
  }

  /**
   * Get the permission grant for a given permission (if any), which contains
   * the accessor to whom the permission was granted.
   *
   * @param itemPerms an item permission set
   * @param permission a permission type
   */
  def getPermission(itemPerms: ItemPermissionSet, permission: PermissionType.Value): Option[Permission] = {
    val accessors = itemPerms.data.flatMap { case (uid, perms) =>
      if (perms.exists(p => PermissionType.in(p, permission))) Some((uid, permission))
      else None
    }
    accessors.headOption.map {
      case (userId, perm) =>
        if (id == userId) Permission(perm)
        else getAccessor(groups, userId) match {
          case Some(u) if u.id == id => Permission(perm)
          case Some(u) => Permission(perm, Some(u.id))
          case x => Permission(perm)
        }
    }
  }
}
