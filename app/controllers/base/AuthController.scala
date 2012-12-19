package controllers.base

import play.api._
import play.api.mvc._
import jp.t2v.lab.play20.auth.Auth
import play.api.libs.concurrent.execution.defaultContext
import models.UserProfile
import defines.EntityType
import models.UserProfileRepr
import models.Entity
import play.api.libs.json.JsValue
import play.api.libs.json.JsString
import models.{Entity,ItemPermissionSet}

/*
 * Wraps optionalUserAction to asyncronously fetch the User's profile.
 */
trait AuthController extends Controller with Auth with Authorizer {

  /**
   * Action composition that adds extra context to regular requests. Namely,
   * the profile of the user requesting the page, and her permissions.
   */
  def userProfileAction(f: Option[User] => Request[AnyContent] => Result): Action[AnyContent] = {
    optionalUserAction { implicit userOption =>
      implicit request =>
        userOption match {
          case Some(user) => {

            // FIXME: This is a DELIBERATE BACKDOOR
            val currentUser = request.getQueryString("asUser").map { name =>
              println("CURRENT USER: " + name)
              println("WARNING: Running with user override backdoor for testing on: ?as=name")
              name
            }.getOrElse(user.profile_id)

            Async {
              // Since we know the user's profile_id we can get the real
              // details by using a fake profile to access their profile as them...
              val fakeProfile = UserProfileRepr(Entity.fromString(currentUser, EntityType.UserProfile))
              val profileRequest = rest.EntityDAO(EntityType.UserProfile, Some(fakeProfile)).get(currentUser)
              val permsRequest = rest.PermissionDAO(fakeProfile).get
              // These requests should execute in parallel...
              for {
                r1 <- profileRequest
                r2 <- permsRequest
              } yield {
                // Check nothing errored horribly...
                if (r1.isLeft) sys.error("Unable to fetch user profile: " + r1.left.get)
                if (r2.isLeft) sys.error("Unable to fetch user permissions: " + r2.left.get)

                // We're okay, so construct the complete profile.
                val u = user.withProfile(UserProfileRepr(r1.right.get)).withPermissions(r2.right.get)
                f(Some(u))(request)
              }
            }
          }
          case None => f(userOption)(request)
        }
    }
  }

  def itemPermAction(id: String)(f: Option[User] => Option[ItemPermissionSet[_]] => Request[AnyContent] => Result): Action[AnyContent] = {
    optionalUserAction { implicit userOption =>
      implicit request =>
        userOption match {
          case Some(user) => {

            // FIXME: This is a DELIBERATE BACKDOOR
            val currentUser = request.getQueryString("asUser").map { name =>
              println("CURRENT USER: " + name)
              println("WARNING: Running with user override backdoor for testing on: ?as=name")
              name
            }.getOrElse(user.profile_id)

            Async {
              // Since we know the user's profile_id we can get the real
              // details by using a fake profile to access their profile as them...
              val fakeProfile = UserProfileRepr(Entity.fromString(currentUser, EntityType.UserProfile))
              val profileRequest = rest.EntityDAO(EntityType.UserProfile, Some(fakeProfile)).get(currentUser)
              val permsRequest = rest.PermissionDAO(fakeProfile).get
              val itemPermRequest = rest.PermissionDAO(fakeProfile).getItem(id)
              // These requests should execute in parallel...
              for {
                r1 <- profileRequest
                r2 <- permsRequest
                r3 <- itemPermRequest
              } yield {
                // Check nothing errored horribly...
                if (r1.isLeft) sys.error("Unable to fetch user profile: " + r1.left.get)
                if (r2.isLeft) sys.error("Unable to fetch user permissions: " + r2.left.get)
                if (r3.isLeft) sys.error("Unable to fetch user permissions for item: " + r3.left.get)
                // We're okay, so construct the complete profile.
                val u = user.withProfile(UserProfileRepr(r1.right.get)).withPermissions(r2.right.get)
                f(Some(u))(Some(r3.right.get))(request)
              }
            }
          }
          case None => f(userOption)(None)(request)
        }
    }
  }

}