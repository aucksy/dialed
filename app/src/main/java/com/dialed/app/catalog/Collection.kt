package com.dialed.app.catalog

/**
 * A browsable collection of faces (docs/DESIGN-ADDENDUM-COLLECTIONS.md §2).
 *
 * DERIVED, not configured: this slice groups the bundled catalog by [Face.series] so the grouping
 * is always honest to what actually ships. When the owner signs off the collection MAP
 * (docs/CATALOG-AUDIT.md §11), this derivation is replaced by `config/catalog.json` — the screens
 * that consume it stay the same; only the data source changes.
 */
data class FaceCollection(
    val id: String,          // the series verbatim, e.g. "Vakt" — unique, stable, save-able
    val title: String,       // display name (== series today)
    val subtitle: String,    // descriptor after "·" in the tag, e.g. "Instrument"; "" if none
    val faces: List<Face>,
) {
    /** First three faces (or fewer) — the vitrine cover trio (§3). */
    val cover: List<Face> get() = faces.take(3)
}

/**
 * Group faces into collections by [Face.series], preserving first-appearance (catalog) order both
 * for the collections and for the faces inside each. `groupBy` keeps encounter order of keys and
 * values, so the result is deterministic.
 */
fun collectionsOf(faces: List<Face>): List<FaceCollection> =
    faces.groupBy { it.series }.map { (series, members) ->
        FaceCollection(
            id = series,
            title = series,
            subtitle = subtitleFrom(members.first().tag),
            faces = members,
        )
    }

/** "Aether · Atmospheric" -> "Atmospheric"; a tag with no "·" -> "". */
private fun subtitleFrom(tag: String): String =
    tag.substringAfter('·', missingDelimiterValue = "").trim()
