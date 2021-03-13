package cookies

import play.api.libs.json.{Json, Reads, Writes}
import play.api.mvc.{RequestHeader, Result}

trait SessionPreferences[T] {

  import SessionPreferences._

  protected val defaultPreferences: T
  
  protected def storeKey: String = DEFAULT_STORE_KEY

  /**
   * Load preferences, ensuring we never error.
   */
  private def load(implicit request: RequestHeader, rds: Reads[T]): T = try {
    (for {
      prefString <- request.session.get(storeKey)
      prefs <- Json.parse(prefString).validate(rds).asOpt
    } yield prefs).getOrElse(defaultPreferences)
  } catch {
    // Ensure that we *never* throw an exception on missing
    // or corrupt preferences...
    case _: Throwable => defaultPreferences
  }

  implicit def preferences(implicit request: RequestHeader, rds: Reads[T]): T = request.preferences

  /**
   * Implicit extension method on RequestHeader to allow preferences
   * class to be straightforwardly loaded.
   */
  implicit class RequestHeaderOps(request: RequestHeader) {
    def preferences(implicit rds: Reads[T]): T = load(request, rds)
  }

  /**
   * Implicit extension method class that can be imported to provide
   * a `withPreferences` method to the Result class.
   */
  implicit class ResultOps(result: Result) {
    def withPreferences(preferences: T)(implicit request: RequestHeader, wts: Writes[T]): Result = {
      result.addingToSession(storeKey -> Json.stringify(Json.toJson(preferences)(wts)))
    }
  }
}

object SessionPreferences {
  val DEFAULT_STORE_KEY = "userPrefs"
}
