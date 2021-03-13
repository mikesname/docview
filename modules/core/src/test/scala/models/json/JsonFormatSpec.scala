package models.json

import helpers.ResourceUtils
import models.{EntityType, _}
import play.api.libs.json.Json
import play.api.test.PlaySpecification

class JsonFormatSpec extends PlaySpecification with ResourceUtils {

  "Documentary Unit Format should read and write with no changes" in {
    val validation = readResource(EntityType.DocumentaryUnit).validate[DocumentaryUnitF]
    // The JSON should parse correctly
    validation.asEither must beRight.which { doc =>
      // Writing the doc and reading it again should produce exactly
      // the same object
      doc mustEqual Json.toJson(doc).as[DocumentaryUnitF]

      // And error when parsing the wrong type
      val badParse = readResource(EntityType.Repository).validate[DocumentaryUnitF]
      badParse.asEither must beLeft
    }
  }

  "Documentary Unit metadata should read correctly" in {
    val doc: DocumentaryUnit = readResource(s"${EntityType.DocumentaryUnit}1.json").as[DocumentaryUnit]
    doc.holder must beSome
    doc.latestEvent must beSome
    doc.meta.value.get("childCount") must beSome
    doc.data.descriptions.headOption must beSome
  }

  "HistoricalAgent Format should read and write with no changes" in {
    val validation = readResource(EntityType.HistoricalAgent).validate[HistoricalAgentF]
    validation.asEither must beRight.which { actor =>
      actor mustEqual Json.toJson(actor).as[HistoricalAgentF]
      val badParse = readResource(EntityType.Repository).validate[HistoricalAgentF]
      badParse.asEither must beLeft
    }
  }

  "Repository Format should read and write with no changes" in {
    val validation = readResource(EntityType.Repository).validate[RepositoryF]
    validation.asEither must beRight.which { repo =>
      repo mustEqual Json.toJson(repo).as[RepositoryF]
      val badParse = readResource(EntityType.HistoricalAgent).validate[RepositoryF]
      badParse.asEither must beLeft
    }
  }

  "Country Format should read and write with no changes" in {
    val validation = readResource(EntityType.Country).validate[CountryF]
    validation.asEither must beRight.which { country =>
      country mustEqual Json.toJson(country).as[CountryF]
      val badParse = readResource(EntityType.Repository).validate[CountryF]
      badParse.asEither must beLeft
    }
  }

  "Repository metadata should read correctly" in {
    val repository: Repository = readResource(s"${EntityType.Repository}1.json").as[Repository]
    repository.country must beSome
    repository.latestEvent must beSome
    repository.meta.value.get("childCount") must beSome
    repository.data.descriptions.headOption must beSome.which { desc =>
      desc.maintenanceEvents.headOption must beSome
    }
  }

  "Concept Format should read and write with no changes" in {
    val validation = readResource(EntityType.Concept).validate[ConceptF]
    validation.asEither must beRight.which { concept =>
      concept mustEqual Json.toJson(concept).as[ConceptF]
      val badParse = readResource(EntityType.Repository).validate[ConceptF]
      badParse.asEither must beLeft
    }
  }

  "Vocabulary Format should read and write with no changes" in {
    val validation = readResource(EntityType.Vocabulary).validate[VocabularyF]
    validation.asEither must beRight.which { vocabulary =>
      vocabulary mustEqual Json.toJson(vocabulary).as[VocabularyF]
      val badParse = readResource(EntityType.Repository).validate[VocabularyF]
      badParse.asEither must beLeft
    }
  }

  "UserProfile Format should read and write with no changes" in {
    val validation = readResource(EntityType.UserProfile).validate[UserProfileF]
    // The JSON should parse correctly
    validation.asEither must beRight.which { user =>
      user mustEqual Json.toJson(user).as[UserProfileF]
      val badParse = readResource(EntityType.Repository).validate[UserProfileF]
      badParse.asEither must beLeft
    }
  }

  "Group Format should read and write with no changes" in {
    val validation = readResource(EntityType.Group).validate[GroupF]
    // The JSON should parse correctly
    validation.asEither must beRight.which { group =>
      group mustEqual Json.toJson(group).as[GroupF]
      val badParse = readResource(EntityType.Repository).validate[GroupF]
      badParse.asEither must beLeft
    }
  }

  "Annotation Format should read and write with no changes" in {
    val validation = readResource(EntityType.Annotation).validate[AnnotationF]
    // The JSON should parse correctly
    validation.asEither must beRight.which { annotation =>
      annotation mustEqual Json.toJson(annotation).as[AnnotationF]
      val badParse = readResource(EntityType.Repository).validate[AnnotationF]
      badParse.asEither must beLeft
    }
  }

  "SystemEvent Format should read correctly" in {
    val validation = readResource(EntityType.SystemEvent).validate[SystemEvent]
    validation.asEither must beRight
  }

  "Virtual Unit Format should read correctly" in {
    val validation = readResource(EntityType.VirtualUnit).validate[VirtualUnit]
    // The JSON should parse correctly
    validation.asEither must beRight.which { virtualUnit =>
      virtualUnit.includedUnits.headOption must beSome

      val validation2 = readResource(EntityType.VirtualUnit.toString + "NoDesc.json").validate[VirtualUnit]
      // The JSON should parse correctly
      validation2.asEither must beRight.which { virtualUnit2 =>
        virtualUnit2.includedUnits.headOption must beNone
      }
    }
  }

  "Content type format should read and write correctly" in {
    val validation = readResource(EntityType.ContentType).validate[DataContentType]
    validation.asEither must beRight
  }
}
