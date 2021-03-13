package models

/**
  * Base trait for a set of attributes that for part
  * of a model class, but does not form a standalone
  * entity in its own right.
  */
trait AttributeSet {
  def toData: Map[String, Any] = getClass.getDeclaredFields.foldLeft(Map[String, Any]()) { (a, f) =>
    f.setAccessible(true)
    a + (f.getName -> toValue(f.get(this)))
  }

  /**
    * Use reflection to determine if this Attribute set contains
    * only None or empty string values - if so it's considered
    * 'empty'. If there are *any* non-Option[String] or non
    * string values this returns false
    */
  def isEmpty: Boolean = {
    getClass.getDeclaredFields.foreach { f =>
      f.setAccessible(true)
      f.get(this) match {
        case opt: Option[_] => if (opt.isDefined) return false
        case s: String => if (s.nonEmpty) return false
        case v => return false
      }
    }
    true
  }

  def toValue(v: Any): Any = v match {
    case Some(value) => value
    case None => None
  }
}
