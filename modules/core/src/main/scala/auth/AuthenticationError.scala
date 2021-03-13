package auth

case class AuthenticationError(msg: String) extends Exception(msg)

object AuthenticationError {
  def apply(msg: String, cause: Throwable): Throwable = new AuthenticationError(msg).initCause(cause)
}
