package forms

import auth.oauth2.OAuth2Config
import config.AppConfig

import javax.inject.{Inject, Singleton}
import models.SignupData
import play.api.data.{Form, Forms}
import play.api.data.Forms._
import models.SignupData._
import play.api.Configuration

@Singleton
case class AccountForms @Inject() (config: Configuration, conf: AppConfig, oAuth2Config: OAuth2Config) {

  import HoneyPotForm._
  import TimeCheckForm._

  val openidForm: Form[String] = Form(single(
    "openid_identifier" -> nonEmptyText
  ).verifying("errors.invalidUrl", forms.isValidUrl))

  val passwordLoginForm: Form[(String, String)] = Form(
    tuple(
      EMAIL -> email,
      PASSWORD -> nonEmptyText
    )
  )

  val changePasswordForm: Form[(String, String, String)] = Form(
    tuple(
      "current" -> nonEmptyText,
      PASSWORD -> nonEmptyText(minLength = conf.minPasswordLength),
      CONFIRM -> nonEmptyText(minLength = conf.minPasswordLength)
    ).verifying("login.error.passwordsDoNotMatch", passwords => passwords._2 == passwords._3)
  )

  val resetPasswordForm: Form[(String, String)] = Form(
    tuple(
      PASSWORD -> nonEmptyText(minLength = conf.minPasswordLength),
      CONFIRM -> nonEmptyText(minLength = conf.minPasswordLength)
    ).verifying("login.error.passwordsDoNotMatch", pc => pc._1 == pc._2)
  )

  val forgotPasswordForm: Form[String] = Form(Forms.single("email" -> email))

  val changeEmailForm: Form[(String, String)] = Form(
    tuple(
      "email" -> email,
      "password" -> nonEmptyText
    )
  )

  //
  // Signup data validation. This does several checks:
  //  - passwords must be over 6 characters
  //  - form must be submitted over 5 seconds after it was rendered
  //  - the blank check field must be present, but left blank (this
  //    is a honeypot check)
  //
  val signupForm: Form[SignupData] = Form(
    mapping(
      NAME -> nonEmptyText,
      EMAIL -> email,
      PASSWORD -> nonEmptyText(minLength = conf.minPasswordLength),
      CONFIRM -> nonEmptyText(minLength = conf.minPasswordLength),
      ALLOW_MESSAGING -> ignored(true),
      TIMESTAMP -> text, // submission time check
      BLANK_CHECK -> text, // honeypot
      AGREE_TERMS -> checked("signup.agreeTerms")
    )(SignupData.apply)(SignupData.unapply)
      .verifying(formSubmissionTime(config))
      .verifying(blankFieldIsBlank)
      .verifying("signup.badPasswords", s => s.password == s.confirm)
  )
}
