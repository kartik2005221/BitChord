package com.music.bitchord.desktop

import com.music.bitchord.data.model.Song
import com.music.bitchord.data.model.durationMillis
import java.lang.Character.UnicodeScript
import java.text.Normalizer
import java.util.Locale
import kotlin.math.abs

/** Conservative cross-catalogue matching for source fallback. */
internal object DesktopTrackMatcher {

    /** A title split into what it is and what is said about it. */
    internal data class TitleParts(
        val words: List<String>,
        val core: String,
        val versions: Set<String>,
        val romanizedAsides: List<List<String>>,
    )

    fun best(candidates: List<Song>, target: Song): Song? = ranked(candidates, target).firstOrNull()

    /** Every candidate that is the same recording, most confident first. */
    fun ranked(candidates: List<Song>, target: Song): List<Song> = candidates
        .mapNotNull { candidate -> score(candidate, target)?.let { candidate to it } }
        .sortedByDescending { it.second }
        .map { it.first }

    /** Whether two rows agree on runtime to within [seconds]. */
    fun withinSeconds(candidate: Song, target: Song, seconds: Int): Boolean {
        val wanted = target.durationMillis().takeIf { it > 0L } ?: return false
        val got = candidate.durationMillis().takeIf { it > 0L } ?: return false
        return abs(wanted - got) <= seconds * 1_000L
    }

    /** What to ask a catalogue for, in the order to ask it. */
    fun queries(target: Song): List<String> {
        val parts = parseTitle(target.title, target.artist)
        if (parts.words.isEmpty()) return emptyList()
        val artist = primaryArtist(target.artist)
        return (listOf(parts.words) + parts.romanizedAsides).flatMap { words ->
            val title = (words + parts.versions).joinToString(" ")
            if (artist.isBlank()) listOf(title) else listOf("$title $artist", title)
        }.distinct()
    }

    /** The title with the packaging taken off, version markers kept. */
    internal fun searchableTitle(title: String, artist: String = ""): String =
        parseTitle(title, artist).let { (it.words + it.versions).joinToString(" ") }

    /** The first credited artist — who a catalogue is most likely to file the track under. */
    internal fun primaryArtist(artist: String): String =
        normalized(artist).split(ARTIST_SEPARATOR).firstOrNull()?.trim().orEmpty()

    private fun score(candidate: Song, target: Song): Int? {
        val wanted = parseTitle(target.title, target.artist)
        val got = parseTitle(candidate.title, candidate.artist)
        if (wanted.core.isEmpty() || got.core.isEmpty()) return null
        val exactTitle = wanted.core == got.core
        if (!exactTitle &&
            !sameFoldedWords(wanted.words, got.words) &&
            wanted.romanizedAsides.none { sameFoldedWords(it, got.words) } &&
            got.romanizedAsides.none { sameFoldedWords(it, wanted.words) }
        ) return null
        // Direction matters both ways round: asking for the album cut must not land on the live
        // take, and asking for the live take must not land on the album cut.
        if (wanted.versions != got.versions) return null

        val targetArtists = artistNames(target.artist)
        val candidateArtists = artistNames(candidate.artist)
        val artistsKnown = targetArtists.isNotEmpty() && candidateArtists.isNotEmpty()
        val exactArtist = targetArtists.any { name -> candidateArtists.any { sameArtist(name, it) } }
        val foldedArtist = !exactArtist && targetArtists.any { name ->
            candidateArtists.any { sameFoldedWords(name, it) }
        }
        if (artistsKnown && !exactArtist && !foldedArtist) return null
        if (!exactTitle && !artistsKnown) return null

        val wantedDuration = target.durationMillis().takeIf { it > 0L }
        val candidateDuration = candidate.durationMillis().takeIf { it > 0L }
        if (wantedDuration != null && candidateDuration != null &&
            abs(wantedDuration - candidateDuration) > DURATION_TOLERANCE_MS
        ) return null

        val artistScore = when {
            !artistsKnown -> 0
            targetArtists == candidateArtists -> 30
            exactArtist -> 20
            else -> 10
        }
        val durationScore = when {
            wantedDuration == null || candidateDuration == null -> 0
            else -> (20 - (abs(wantedDuration - candidateDuration) / 1_000L).toInt()).coerceAtLeast(0)
        }
        val spellingScore = when {
            exactTitle && exactArtist -> 120
            exactTitle -> 60
            else -> 0
        }
        return spellingScore + artistScore + durationScore
    }

    /** A title split into its core words and the qualifiers around them. */
    internal fun parseTitle(raw: String, artist: String = ""): TitleParts {
        val versions = sortedSetOf<String>()
        val asides = mutableListOf<List<String>>()
        var text = normalized(raw)
            .replace('（', '(').replace('）', ')')
            .replace("&", " and ")

        // Bracketed asides, innermost first: (From "Awarapan 2"), [Official Audio], (Live at
        // Wembley).
        repeat(BRACKET_PASSES) {
            if (!BRACKETED.containsMatchIn(text)) return@repeat
            text = BRACKETED.replace(text) { match ->
                val segment = match.groupValues[1]
                classify(segment, versions)
                if (match.value.startsWith('(')) {
                    romanizedAside(segment)?.let(asides::add)
                }
                " "
            }
        }
        // An unbalanced bracket — a title truncated mid-aside — takes the rest of the line with it
        // rather than leaving half an aside in the core.
        text.indexOfFirst { it == '(' || it == '[' }.takeIf { it >= 0 }?.let { open ->
            classify(text.substring(open), versions)
            text = text.substring(0, open)
        }
        // Dash- and pipe-separated tails.
        repeat(DASH_PASSES) {
            val dash = DASH.find(text) ?: return@repeat
            val head = text.substring(0, dash.range.first)
            val tail = text.substring(dash.range.last + 1)
            text = if (isArtistName(head, artist)) {
                classify(head, versions)
                tail
            } else {
                classify(tail, versions)
                head
            }
        }
        // A feat.
        text = text.replace(FEATURING, " ")

        var words = words(text).filterNot { it in JOINING_WORDS }
        // An upload's trailing label, printed without brackets to hang it on.
        while (words.size > 1 && words.last() in TRAILING_NOISE) {
            words = words.dropLast(1)
        }
        val core = words.joinToString("")
        val romanizedAsides = if (core.codePoints().anyMatch { UnicodeScript.of(it) in CJK_SCRIPTS }) {
            asides
        } else {
            emptyList()
        }
        return TitleParts(words, core, versions, romanizedAsides)
    }

    private fun romanizedAside(segment: String): List<String>? {
        val words = words(segment)
        if (words.isEmpty() || words.any { it in VERSION_WORDS || it in TRAILING_NOISE } ||
            words.first() in ASIDE_PREFIXES || !LATIN_ASIDE.matches(segment) ||
            !LATIN_LETTER.containsMatchIn(segment)
        ) return null
        return words.filterNot { it in JOINING_WORDS }.ifEmpty { null }
    }

    /** Files one dropped segment under [versions], or discards it. */
    private fun classify(segment: String, versions: MutableSet<String>) {
        val words = words(segment)
        if (words.isEmpty()) return
        if (words.joinToString("") in NEUTRAL_SEGMENTS) return
        val marks = words.filter { it in VERSION_WORDS }
        if (marks.isNotEmpty()) versions += marks
    }

    private fun isArtistName(text: String, artist: String): Boolean {
        if (artist.isBlank()) return false
        val head = words(text)
        if (head.isEmpty()) return false
        val credited = words(normalized(artist))
        return head.all { it in credited } ||
            artistNames(artist).any { sameFoldedWords(head, it) }
    }

    private fun artistNames(value: String): Set<List<String>> {
        val normalized = normalized(value).trim()
        if (normalized.isUnknownArtist()) return emptySet()
        return normalized
            .split(ARTIST_SEPARATOR)
            .map { name ->
                words(name).filterNot { it.length == 1 && it[0] in 'a'..'z' }
            }
            .filter { it.isNotEmpty() }
            .toSet()
    }

    private fun String.isUnknownArtist(): Boolean =
        this in setOf("unknown", "unknown artist", "unknown artist(s)")

    private fun sameArtist(first: List<String>, second: List<String>): Boolean =
        containsWords(first, second) || containsWords(second, first)

    private fun sameFoldedWords(first: List<String>, second: List<String>): Boolean =
        first.size == second.size && first.indices.all { index ->
            val a = first[index]
            val b = second[index]
            // One catalogue may omit a mark. A different mark is separate evidence.
            a == b || foldLatin(a) == b || a == foldLatin(b)
        }

    private fun normalized(value: String): String =
        Normalizer.normalize(value.lowercase(Locale.ROOT), Normalizer.Form.NFC)

    private fun words(value: String): List<String> =
        value.split(WORD_SEPARATOR)
            .map { it.replace(OUTSIDE_WORD, "").replace(LEADING_MARKS, "") }
            .filter { it.isNotEmpty() }

    private fun foldLatin(value: String): String = buildString(value.length) {
        var latinBase = false
        Normalizer.normalize(value, Normalizer.Form.NFD).codePoints().forEach { point ->
            if (!COMBINING_MARK_TYPES.contains(Character.getType(point))) {
                latinBase = UnicodeScript.of(point) == UnicodeScript.LATIN
                appendCodePoint(if (point == 'ı'.code) 'i'.code else point)
            } else if (!latinBase) {
                appendCodePoint(point)
            }
        }
    }.let { Normalizer.normalize(it, Normalizer.Form.NFC) }

    private fun containsWords(outer: List<String>, inner: List<String>): Boolean =
        inner.isNotEmpty() && inner.size <= outer.size &&
            (0..outer.size - inner.size).any { start ->
                outer.subList(start, start + inner.size) == inner
            }

    private const val BRACKET_PASSES = 3
    private const val DASH_PASSES = 3

    private val WORD_SEPARATOR = Regex("""[\s\p{Z}.·]+""")
    private val OUTSIDE_WORD = Regex("""[^\p{L}\p{M}\p{N}]""")
    private val LEADING_MARKS = Regex("""^\p{M}+""")
    private val LATIN_LETTER = Regex("""\p{IsLatin}""")
    private val LATIN_ASIDE = Regex("""[\p{IsLatin}\p{M}\p{N}\p{P}\p{Z}\s]+""")
    private val ASIDE_PREFIXES = setOf("from", "feat", "ft", "featuring", "with")
    private val CJK_SCRIPTS = setOf(
        UnicodeScript.HAN, UnicodeScript.HIRAGANA, UnicodeScript.KATAKANA, UnicodeScript.HANGUL,
    )
    private val COMBINING_MARK_TYPES = setOf(
        Character.NON_SPACING_MARK.toInt(),
        Character.COMBINING_SPACING_MARK.toInt(),
        Character.ENCLOSING_MARK.toInt(),
    )
    private val BRACKETED = Regex("""[(\[]([^()\[\]]*)[)\]]""")
    private val DASH = Regex("""\s+[-–—|]+\s+""")
    private val FEATURING = Regex("""\b(feat|ft|featuring|with)\b.*""")
    private val ARTIST_SEPARATOR =
        Regex("""\s*(?:[,&/;·|]|\band\b|\bx\b|\bvs\.?\b|\bfeat\.?\b|\bft\.?\b|\bfeaturing\b|\bwith\b)\s*""")

    private val VERSION_WORDS = setOf(
        "remix", "remixes", "rmx", "refix", "flip", "bootleg", "mashup", "medley",
        "live", "concert", "unplugged", "acoustic", "instrumental", "karaoke",
        // A stem is not the song: "Vocals Only", "Acapella" and friends carry the right title and
        // the right artist and are not remotely the recording anybody asked for.
        "vocals", "vocal", "acapella", "acappella", "backing", "stems", "stem",
        "cover", "demo", "reprise", "remake", "rework", "extended", "edit",
        "version", "mix", "dub", "vip", "session", "sessions",
        "sped", "slowed", "reverb", "nightcore", "lofi", "orchestral", "symphonic",
        "part", "pt", "chapter",
    )

    private val NEUTRAL_SEGMENTS = setOf(
        "albumversion", "originalversion", "originalmix", "singleversion",
        "radioversion", "radioedit", "stereoversion", "monoversion",
        "studioversion", "fullversion", "standardversion", "explicitversion",
        "deluxeversion", "originaltrack",
    )

    private val TRAILING_NOISE = setOf(
        "song", "songs", "video", "audio", "lyrics", "lyric", "lyrical",
        "official", "full", "hd", "hq", "4k", "mp3", "ost", "soundtrack",
    )

    private val JOINING_WORDS = setOf("and")

    private const val DURATION_TOLERANCE_MS = 30_000L
}
