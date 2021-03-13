import org.apache.commons.text.WordUtils
import play.api.i18n.Messages

import java.util.Locale

package object i18n {

  val defaultLang: String = Locale.getDefault.getISO3Language

  def languagePairList(implicit messages: Messages): List[(String, String)] = {
    val locale = messages.lang.toLocale
    val localeLangs = lang3to2lookup.map { case (c3, c2) =>
      c3 -> WordUtils.capitalize(new java.util.Locale(c2).getDisplayLanguage(locale))
    }.toList

    (localeLangs ::: additionalLanguages.toList.map(l => l -> Messages("languageCode." + l))).sortBy(_._2)
  }

  /**
    * Lazily build a lookup of ISO 639-2 (3-letter) to 639-1 (2-letter) codes
    */
  lazy val lang3to2lookup: Map[String, String] = Locale.getISOLanguages.flatMap { code =>
    new Locale(code, "").getISO3Language match {
      case c3 if c3 != "" => Some(c3 -> code)
      case _ => Nil
    }
  }.toMap

  /**
    * Lazily build a lookup of ISO 639-2 (3-letter) to 639-1 (2-letter) codes
    */
  lazy val lang2to3lookup: Map[String, String] = Locale.getISOLanguages.flatMap { code =>
    new Locale(code, "").getISO3Language match {
      case c3 if c3 != "" => Some(code -> c3)
      case _ => Nil
    }
  }.toMap

  /**
    * Get the name for a language, if we can find one.
    */
  def languageCode2ToNameOpt(code: String)(implicit messages: Messages): Option[String] = {
    new Locale(code, "").getDisplayLanguage(messages.lang.toLocale) match {
      case d if d.nonEmpty => Some(d)
      case _ => None
    }
  }

  /**
    * Get a language name for a given code.
    */
  def languageCodeToName(code: String)(implicit messages: Messages): String = code match {
    case c if additionalLanguages.contains(c) => Messages(s"languageCode.$c")
    case c if c.length == 2 => languageCode2ToNameOpt(code).getOrElse(code)
    case c => lang3to2lookup.get(c)
      .flatMap(c2 => languageCode2ToNameOpt(c2))
      .getOrElse(c)
  }

  def scriptPairList(messages: Messages): List[(String, String)] = scripts.sortBy(_._2)

  /**
    * Get the script name for a given code. This doesn't work with Java 6 so we have to sacrifice
    * localised script names. On Java 7 we'd do:
    *
    * var tmploc = new Locale.Builder().setScript(code).build()
    * tmploc.getDisplayScript(lang.toLocale) match {
    * case d if d.nonEmpty => d
    * case _ => code
    * }
    */
  def scriptCodeToName(code: String)(implicit messages: Messages): String = {
    try {
      // NB: Current ignores lang...
      scripts.toMap.getOrElse(code, code)
    } catch {
      // This should be an IllformedLocaleException
      // but we need to work with Java 6
      case _: Exception => code
    }
  }

  /**
    * Convert a country code to a name, first trying
    * the localised JDK locale names, and falling back
    * on our i18n messages file.
    */
  def countryCodeToName(code: String)(implicit messages: Messages): String = {
    val key = "countryCode." + code.toLowerCase
    val i18n = Messages(key)
    if (i18n != key) i18n else {
      new Locale("", code).getDisplayCountry(messages.lang.toLocale) match {
        case d if d.nonEmpty => d
        case _ => code
      }
    }
  }

  /**
    * Get a list of country->name pairs for the given language.
    */
  def countryPairList(implicit messages: Messages): List[(String, String)] = {
    val locale = messages.lang.toLocale
    java.util.Locale.getISOCountries.map { code =>
      code -> WordUtils.capitalize(countryCodeToName(code))
    }.toList.sortBy(_._2)
  }

  /**
    * Additional languages not supported by Java Locale. These should
    * have a translation in the messages with the prefix "languageCode".
    */
  val additionalLanguages = Set(
    "lad", // Ladino
    "mul", // Multiple
    "sh" // Serbo-Croatian
  )

  /**
    * Script mappings for ISO15924. NB: These are not localised.
    *
    * Gleaned from: http://unicode.org/iso15924/iso15924-codes.html
    * (with long items slightly truncated for display.)
    */
  val scripts = List(
    "Afak" -> "Afaka",
    "Aghb" -> "Caucasian Albanian",
    "Arab" -> "Arabic",
    "Armi" -> "Imperial Aramaic",
    "Armn" -> "Armenian",
    "Avst" -> "Avestan",
    "Bali" -> "Balinese",
    "Bamu" -> "Bamum",
    "Bass" -> "Bassa Vah",
    "Batk" -> "Batak",
    "Beng" -> "Bengali",
    "Blis" -> "Blissymbols",
    "Bopo" -> "Bopomofo",
    "Brah" -> "Brahmi",
    "Brai" -> "Braille",
    "Bugi" -> "Buginese",
    "Buhd" -> "Buhid",
    "Cakm" -> "Chakma",
    "Cans" -> "Unified Canadian Aboriginal Syllabics",
    "Cari" -> "Carian",
    "Cham" -> "Cham",
    "Cher" -> "Cherokee",
    "Cirt" -> "Cirth",
    "Copt" -> "Coptic",
    "Cprt" -> "Cypriot",
    "Cyrl" -> "Cyrillic",
    "Cyrs" -> "Cyrillic (Old Church Slavonic variant)",
    "Deva" -> "Devanagari (Nagari)",
    "Dsrt" -> "Deseret (Mormon)",
    "Dupl" -> "Duployan shorthand",
    "Egyd" -> "Egyptian demotic",
    "Egyh" -> "Egyptian hieratic",
    "Egyp" -> "Egyptian hieroglyphs",
    "Elba" -> "Elbasan",
    "Ethi" -> "Ethiopic (Geʻez)",
    "Geok" -> "Khutsuri (Asomtavruli and Nuskhuri)",
    "Geor" -> "Georgian (Mkhedruli)",
    "Glag" -> "Glagolitic",
    "Goth" -> "Gothic",
    "Gran" -> "Grantha",
    "Grek" -> "Greek",
    "Gujr" -> "Gujarati",
    "Guru" -> "Gurmukhi",
    "Hang" -> "Hangul (Hangŭl, Hangeul)",
    "Hani" -> "Han (Hanzi, Kanji, Hanja)",
    "Hano" -> "Hanunoo (Hanunóo)",
    "Hans" -> "Han (Simplified variant)",
    "Hant" -> "Han (Traditional variant)",
    "Hebr" -> "Hebrew",
    "Hira" -> "Hiragana",
    "Hluw" -> "Anatolian Hieroglyphs",
    "Hmng" -> "Pahawh Hmong",
    "Hrkt" -> "Japanese syllabaries",
    "Hung" -> "Old Hungarian (Hungarian Runic)",
    "Inds" -> "Indus (Harappan)",
    "Ital" -> "Old Italic",
    "Java" -> "Javanese",
    "Jpan" -> "Japanese",
    "Jurc" -> "Jurchen",
    "Kali" -> "Kayah Li",
    "Kana" -> "Katakana",
    "Khar" -> "Kharoshthi",
    "Khmr" -> "Khmer",
    "Khoj" -> "Khojki",
    "Knda" -> "Kannada",
    "Kore" -> "Korean (alias for Hangul + Han)",
    "Kpel" -> "Kpelle",
    "Kthi" -> "Kaithi",
    "Lana" -> "Tai Tham (Lanna)",
    "Laoo" -> "Lao",
    "Latf" -> "Latin (Fraktur variant)",
    "Latg" -> "Latin (Gaelic variant)",
    "Latn" -> "Latin",
    "Lepc" -> "Lepcha (Róng)",
    "Limb" -> "Limbu",
    "Lina" -> "Linear A",
    "Linb" -> "Linear B",
    "Lisu" -> "Lisu (Fraser)",
    "Loma" -> "Loma",
    "Lyci" -> "Lycian",
    "Lydi" -> "Lydian",
    "Mahj" -> "Mahajani",
    "Mand" -> "Mandaic, Mandaean",
    "Mani" -> "Manichaean",
    "Maya" -> "Mayan hieroglyphs",
    "Mend" -> "Mende",
    "Merc" -> "Meroitic Cursive",
    "Mero" -> "Meroitic Hieroglyphs",
    "Mlym" -> "Malayalam",
    "Mong" -> "Mongolian",
    "Moon" -> "Moon",
    "Mroo" -> "Mro, Mru",
    "Mtei" -> "Meitei Mayek (Meithei, Meetei)",
    "Mymr" -> "Myanmar (Burmese)",
    "Narb" -> "Old North Arabian",
    "Nbat" -> "Nabataean",
    "Nkgb" -> "Nakhi Geba",
    "Nkoo" -> "N’Ko",
    "Nshu" -> "Nüshu",
    "Ogam" -> "Ogham",
    "Olck" -> "Ol Chiki (Ol Cemet’, Ol, Santali)",
    "Orkh" -> "Old Turkic, Orkhon Runic",
    "Orya" -> "Oriya",
    "Osma" -> "Osmanya",
    "Palm" -> "Palmyrene",
    "Perm" -> "Old Permic",
    "Phag" -> "Phags-pa",
    "Phli" -> "Inscriptional Pahlavi",
    "Phlp" -> "Psalter Pahlavi",
    "Phlv" -> "Book Pahlavi",
    "Phnx" -> "Phoenician",
    "Plrd" -> "Miao (Pollard)",
    "Prti" -> "Inscriptional Parthian",
    "Qaaa" -> "Reserved for private use (start)",
    "Qabx" -> "Reserved for private use (end)",
    "Rjng" -> "Rejang (Redjang, Kaganga)",
    "Roro" -> "Rongorongo",
    "Runr" -> "Runic",
    "Samr" -> "Samaritan",
    "Sara" -> "Sarati",
    "Sarb" -> "Old South Arabian",
    "Saur" -> "Saurashtra",
    "Sgnw" -> "SignWriting",
    "Shaw" -> "Shavian (Shaw)",
    "Shrd" -> "Sharada, Śāradā",
    "Sind" -> "Khudawadi, Sindhi",
    "Sinh" -> "Sinhala",
    "Sora" -> "Sora Sompeng",
    "Sund" -> "Sundanese",
    "Sylo" -> "Syloti Nagri",
    "Syrc" -> "Syriac",
    "Syre" -> "Syriac (Estrangelo variant)",
    "Syrj" -> "Syriac (Western variant)",
    "Syrn" -> "Syriac (Eastern variant)",
    "Tagb" -> "Tagbanwa",
    "Takr" -> "Takri, Ṭākrī, Ṭāṅkrī",
    "Tale" -> "Tai Le",
    "Talu" -> "New Tai Lue",
    "Taml" -> "Tamil",
    "Tang" -> "Tangut",
    "Tavt" -> "Tai Viet",
    "Telu" -> "Telugu",
    "Teng" -> "Tengwar",
    "Tfng" -> "Tifinagh (Berber)",
    "Tglg" -> "Tagalog (Baybayin, Alibata)",
    "Thaa" -> "Thaana",
    "Thai" -> "Thai",
    "Tibt" -> "Tibetan",
    "Tirh" -> "Tirhuta",
    "Ugar" -> "Ugaritic",
    "Vaii" -> "Vai",
    "Visp" -> "Visible Speech",
    "Wara" -> "Warang Citi (Varang Kshiti)",
    "Wole" -> "Woleai",
    "Xpeo" -> "Old Persian",
    "Xsux" -> "Cuneiform, Sumero-Akkadian",
    "Yiii" -> "Yi",
    "Zinh" -> "Code for inherited script",
    "Zmth" -> "Mathematical notation",
    "Zsym" -> "Symbols",
    "Zxxx" -> "Code for unwritten documents",
    "Zyyy" -> "Code for undetermined script",
    "Zzzz" -> "Code for uncoded script"
  )
}
