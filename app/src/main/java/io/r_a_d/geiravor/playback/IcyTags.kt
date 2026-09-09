package io.r_a_d.geiravor.playback

/**
 * Live DJ tags ride ICY metadata (`Icy-Tags` / `StreamTags`), not `/api` `tags`
 * (that field is JSON null when there is no catalog track). `docs/spec/api.md`.
 */
object IcyTags {
    private val PAIR = Regex("([A-Za-z0-9_-]+)='([^']*)'")
    private val KEYS = setOf("icy-tags", "icytags", "streamtags", "tags")
    private val SPLIT = Regex("[,\\s]+")

    fun parse(raw: String): List<String> {
        val pairs = LinkedHashMap<String, String>()
        PAIR.findAll(raw).forEach { m ->
            pairs[m.groupValues[1].lowercase()] = m.groupValues[2]
        }
        val value = KEYS.firstNotNullOfOrNull { pairs[it] }?.trim().orEmpty()
        if (value.isEmpty()) return emptyList()
        return value.split(SPLIT).map { it.trim() }.filter { it.isNotEmpty() }
    }

    fun shown(api: List<String>, icy: List<String>): List<String> =
        if (api.isNotEmpty()) api else icy
}
