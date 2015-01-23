package integration.portal

import auth.HashedPassword
import helpers.IntegrationTestRunner
import models.SignupData
import play.api.cache.Cache
import play.api.test.FakeRequest
import utils.forms.HoneyPotForm._
import utils.forms.TimeCheckForm._

class AccountsSpec extends IntegrationTestRunner {
  import mocks.privilegedUser

  private val accountRoutes = controllers.portal.account.routes.Accounts

  "Account views" should {
    "redirect to index page on log out" in new ITestApp {
      val logout = route(fakeLoggedInHtmlRequest(privilegedUser, GET,
        accountRoutes.logout().url)).get
      status(logout) must equalTo(SEE_OTHER)
      flash(logout).get("success") must beSome.which { fl =>
        // NB: No i18n here...
        fl must contain("logout.confirmation")
      }
    }

    "allow user to login with password" in new ITestApp(
        specificConfig = Map(
          "recaptcha.skip" -> true,
          "ehri.signup.timeCheckSeconds" -> -1
        )
      ) {
      val data: Map[String,Seq[String]] = Map(
        SignupData.EMAIL -> Seq(privilegedUser.email),
        SignupData.PASSWORD -> Seq(testPassword),
        TIMESTAMP -> Seq(org.joda.time.DateTime.now.toString),
        BLANK_CHECK -> Seq(""),
        CSRF_TOKEN_NAME -> Seq(fakeCsrfString)
      )

      await(mockAccounts.update(privilegedUser
        .copy(password = Some(HashedPassword.fromPlain(testPassword)))))
      val login = route(FakeRequest(POST,
        accountRoutes.passwordLoginPost.url)
        .withSession(CSRF_TOKEN_NAME -> fakeCsrfString), data).get
      status(login) must equalTo(SEE_OTHER)
    }

    "allow user to login via OAuth2" in new ITestApp {
      // Using the existing Google OAuth2 association
      // in the mock database. The key here is that
      // the data in the `googleUserData.txt` resource
      // contains the same email (example1@example.com) as
      // a user in the mocks DB (mike)
      val singleUseKey = "useOnce"
      val randomState = "473284374"
      Cache.set(singleUseKey, randomState)
      val login = route(FakeRequest(GET,
        s"${accountRoutes.googleLogin.url}?code=blah&state=$randomState")
        .withSession("sid" -> singleUseKey)).get
      println(contentAsString(login))
      status(login) must equalTo(SEE_OTHER)
    }
  }
}
