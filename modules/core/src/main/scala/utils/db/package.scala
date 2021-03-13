package utils

import org.apache.commons.lang3.RandomUtils

package object db {
  /**
    * Generate a random alphanumeric string for an object ID.
    *
    * This is derived from Parse's newObjectId() function,
    * for compatibility with item's originating in Parse's
    * hosted service.
    *
    * @param length the ID length
    * @return a random alphanumeric string
    */
  def newObjectId(length: Int): String = {
    // Implementation of Parse's newObjectId() which generates
    // a random 10 digit string (not checked for uniqueness...)
    val chars: String =
      "ABCDEFGHIJKLMNOPQRSTUVWXYZ" +
        "abcdefghijklmnopqrstuvwxyz" +
        "0123456789"
    new String(RandomUtils.nextBytes(length)
      .map(b => chars.charAt((b & 0xff) % chars.length)))
  }
}
