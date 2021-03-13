package views

object NoopHighlighter extends Highlighter {
  def highlight(text: String): String = text
}

trait Highlighter {
  def highlight(text: String): String
}
