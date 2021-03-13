package services.search

/**
 * Various Solr-related constant values.
 */
object SearchConstants {

  /**
   * ID field
   */
  final val ID = "id"

  /**
   * Field denoting if material is restricted.
   */
  final val RESTRICTED_FIELD = "restricted"

  /**
    * Field that holds a documents accessors.
    */
  final val ACCESSOR_FIELD = "accessibleTo"

  /**
   * Placeholder value for when a document is
   * accessible to everybody. This is necessary because
   * there is apparently no way to filter docs where
   * a given multivalue field is empty.
   */
  final val ACCESSOR_ALL_PLACEHOLDER = "ALLUSERS"

  /**
   * Items contained by a 'holder' item
   */
  final val CHILD_COUNT = "childCount"

  /**
   * Approx number of characters in the item text
   */
  final val CHAR_COUNT = "charCount"

  /**
   * Local identifier
   */
  final val IDENTIFIER = "identifier"

  /**
    * Alternative identifiers.
    */
  final val OTHER_IDENTIFIERS = "otherIdentifiers"

  /**
   * The canonical "item name"
   */
  final val NAME_EXACT = "name"

  /**
   * Other forms of name
   */
  final val OTHER_NAMES = "otherFormsOfName"

  /**
   * Parallel forms of name
   */
  final val PARALLEL_NAMES = "parallelFormsOfName"

  /**
   * All alternate names of name
   */
  final val ALT_NAMES = "altName"

  /**
   * Name field for word matches
   */
  final val NAME_MATCH = "title" // FIXME???

  /**
   * Name field used for sorting.
   */
  final val NAME_SORT = "name_sort"

  /**
   * Name field used for ngram matching.
   */
  final val NAME_NGRAM = "name_ngram"

  /**
   * The field referring to the "item"'s id, rather than
   * that of the description.
   */
  final val ITEM_ID = "itemId"

  /**
   * The field referring to the database internal representation
   * of the item.
   */
  final val DB_ID = "gid"

  /**
   * The type-of-item key
   */
  final val TYPE = "type"

  /**
   * Field that contains everything else as concatenated text.
   */
  final val TEXT = "text"

  /**
   * Field containing the last-modified date
   */
  final val LAST_MODIFIED = "lastUpdated"

  /**
   * Language code for descriptions
   */
  final val LANGUAGE_CODE = "languageCode"

  /**
   * Default limit, if none is set
   */
  final val DEFAULT_SEARCH_LIMIT = 20

  /**
   * Default filter limit.
   */
  final val DEFAULT_FILTER_LIMIT = 100

  /**
   * Parent id for hierarchical items.
   */
  final val PARENT_ID = "parentId"

  /**
   * Items that have children
   */
  final val IS_PARENT = "isParent"

  /**
   * Holder id for hierarchical items.
   */
  final val HOLDER_ID = "holderId"

  /**
   * List of parent, grand-parent, etc.
   */
  final val ANCESTOR_IDS = "ancestorIds"

  /**
   * Annotator id for notes and links
   */
  final val ANNOTATOR_ID = "annotatorId"

  /**
   * Holder name for hierarchical items.
   */
  final val HOLDER_NAME = "holderName"

  /**
   * Items that are are the top level of a hierarchy
   */
  final val TOP_LEVEL = "isTopLevel"

  /**
   * Country code.
   */
  final val COUNTRY_CODE = "countryCode"

  /**
   * Active item boolean
   */
  final val ACTIVE = "active"

  /**
   * Created by EHRI or a partner
   */
  final val CREATION_PROCESS = "creationProcess"
}
