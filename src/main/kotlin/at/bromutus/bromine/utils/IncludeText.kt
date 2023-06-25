package at.bromutus.bromine.utils


fun includeText(alwaysInclude: String?, prompt: String?): String? = when {
    alwaysInclude == null -> prompt
    prompt == null -> alwaysInclude
    else -> "$alwaysInclude, ${prompt.replace(" AND ", " AND $alwaysInclude, ")}"
}