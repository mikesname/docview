package models

import helpers.ResourceUtils
import play.api.test.PlaySpecification

class FormSpec extends PlaySpecification with ResourceUtils {
  "Documentary Unit Form should read and write with no changes" in {
    val data = readResource(EntityType.DocumentaryUnit).as[DocumentaryUnitF]
    val form = DocumentaryUnit.form.fillAndValidate(data)
    form.errors must beEmpty
    DocumentaryUnit.form.bind(form.data).value must beSome(data)
  }

  "HistoricalAgent Form should read and write with no changes" in {
    val data = readResource(EntityType.HistoricalAgent).as[HistoricalAgentF]
    val form = HistoricalAgent.form.fillAndValidate(data)
    form.errors must beEmpty
    HistoricalAgent.form.bind(form.data).value must beSome(data)
  }

  "Repository Form should read and write with no changes" in {
    val data = readResource(EntityType.Repository).as[RepositoryF]
    val form = Repository.form.fillAndValidate(data)
    form.errors must beEmpty
    Repository.form.bind(form.data).value must beSome(data)
  }

  "Country Form should read and write with no changes" in {
    val data = readResource(EntityType.Country).as[CountryF]
    val form = Country.form.fillAndValidate(data)
    form.errors must beEmpty
    Country.form.bind(form.data).value must beSome(data)
  }

  "Concept Form should read and write with no changes" in {
    val data = readResource(EntityType.Concept).as[ConceptF]
    val form = Concept.form.fillAndValidate(data)
    form.errors must beEmpty
    Concept.form.bind(form.data).value must beSome(data)
  }

  "Vocabulary Form should read and write with no changes" in {
    val data = readResource(EntityType.Vocabulary).as[VocabularyF]
    val form = Vocabulary.form.fillAndValidate(data)
    form.errors must beEmpty
    Vocabulary.form.bind(form.data).value must beSome(data)
  }

  "UserProfile Form should read and write with no changes" in {
    val data = readResource(EntityType.UserProfile).as[UserProfileF]
    val form = UserProfile.form.fillAndValidate(data)
    form.errors must beEmpty
    UserProfile.form.bind(form.data).value must beSome(data)
  }

  "Group Form should read and write with no changes" in {
    val data = readResource(EntityType.Group).as[GroupF]
    val form = Group.form.fillAndValidate(data)
    form.errors must beEmpty
    Group.form.bind(form.data).value must beSome(data)
  }

  "Annotation Form should read and write with no changes" in {
    val data = readResource(EntityType.Annotation).as[AnnotationF]
    val form = Annotation.form.fillAndValidate(data)
    form.errors must beEmpty
    Annotation.form.bind(form.data).value must beSome(data)
  }

  "Virtual Unit Form should read correctly" in {
    val data = readResource(EntityType.VirtualUnit).as[VirtualUnitF]
    val form = VirtualUnit.form.fillAndValidate(data)
    form.errors must beEmpty
    VirtualUnit.form.bind(form.data).value must beSome(data)
  }
}
