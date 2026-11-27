package com.music.bitchord.desktop

import com.music.bitchord.data.model.Song
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/** The case that made addons useless outside search. */
class DesktopMatcherTest {

    private val youtubeRow = Song(
        videoId = "kb_6Vz-k7Wc",
        title = "Tera Mera Rishta -  New Version (From \"Awarapan 2\")",
        artist = "Mithoon, Saaj Bhatt, Sayeed Quadri & Mustafa Zahid",
        thumbnailUrl = null,
    )

    @Test
    fun aCatalogueIsAskedForTheTitleWithoutItsPackaging() {
        val queries = DesktopTrackMatcher.queries(youtubeRow)

        // The film, and the four-way credit, are not part of the question.
        assertEquals(listOf("tera mera rishta version mithoon", "tera mera rishta version"), queries)
    }

    @Test
    fun theCatalogueListingOfTheSameTakeMatchesADecoratedYouTubeTitle() {
        // What JioSaavn actually files this under, film and extra credits and upload decoration all
        // absent.
        val catalogueRow = Song(
            videoId = "jiosaavn:wfTj8NYC",
            title = "Tera Mera Rishta - New Version",
            artist = "Mithoon",
            thumbnailUrl = null,
            durationText = "6:05",
        )

        assertNotNull(DesktopTrackMatcher.best(listOf(catalogueRow), youtubeRow))
    }

    @Test
    fun aDifferentTakeOfTheSameSongIsStillRefused() {
        // "New Version" is a take, so the plain listing is a different recording and must not stand
        // in for it.
        val plainListing = Song("jiosaavn:other", "Tera Mera Rishta", "Mithoon", null)

        assertNull(DesktopTrackMatcher.best(listOf(plainListing), youtubeRow))
    }

    @Test
    fun theUploadConventionOfArtistFirstIsUnderstood() {
        // "Artist - Title" hands the title over to the tail rather than eating it, so this is the
        // same recording as a catalogue's plain listing.
        val upload = Song("v", "Mithoon - Tera Mera Rishta (Official Video)", "Mithoon", null)

        assertEquals("tera mera rishta", DesktopTrackMatcher.searchableTitle(upload.title, upload.artist))
    }

    @Test
    fun aTakeIsIdentityAndHasToAgree() {
        val target = Song("v", "Song Title", "An Artist", null)
        val live = Song("a", "Song Title (Live)", "An Artist", null)
        val album = Song("b", "Song Title", "An Artist", null)

        // Asking for the album cut must not land on the live take…
        assertNull(DesktopTrackMatcher.best(listOf(live), target))
        // …and asking for the live take must not land on the album cut.
        assertNull(DesktopTrackMatcher.best(listOf(album), live))
        assertNotNull(DesktopTrackMatcher.best(listOf(album), target))
    }

    @Test
    fun packagingThatOnlyLooksLikeATakeIsIgnored() {
        val target = Song("v", "Song Title (Album Version)", "An Artist", null)
        val plain = Song("a", "Song Title", "An Artist", null)

        // "Album Version" describes the ordinary release; treating it as a take would stop a source
        // ever matching the plain listing.
        assertNotNull(DesktopTrackMatcher.best(listOf(plain), target))
    }

    @Test
    fun anUploadsTrailingLabelIsNotPartOfTheTitle() {
        assertEquals("paniyon sa", DesktopTrackMatcher.searchableTitle("Paniyon Sa Full Song", ""))
        // Never stripped to nothing: a track really called "Song" keeps its name.
        assertEquals("song", DesktopTrackMatcher.searchableTitle("Song", ""))
    }

    @Test
    fun onlyTheFirstCreditedArtistGoesIntoTheQuery() {
        assertEquals("mithoon", DesktopTrackMatcher.primaryArtist("Mithoon, Saaj Bhatt & Sayeed Quadri"))
        assertEquals("arijit singh", DesktopTrackMatcher.primaryArtist("Arijit Singh feat. Someone"))
    }

    @Test
    fun latinAccentsAndTurkishDotlessIAreOptionalInComparisons() {
        val target = song("Şımarık", "Müslüm Gürses")
        val unaccented = song("Simarik", "Muslum Gurses")
        val decomposed = song("S\u0327ımarık", "Mu\u0308slu\u0308m Gu\u0308rses")

        assertEquals(unaccented, DesktopTrackMatcher.best(listOf(unaccented), target))
        assertEquals(target, DesktopTrackMatcher.best(listOf(target), unaccented))
        assertEquals(decomposed, DesktopTrackMatcher.best(listOf(decomposed), target))
        assertEquals(listOf("şımarık müslüm gürses", "şımarık"), DesktopTrackMatcher.queries(decomposed))
    }

    @Test
    fun accentedArtistHeadersAreRemovedFromProviderQueries() {
        val target = song("Muslum Gurses - Nilüfer (Official Video)", "Müslüm Gürses")

        assertEquals(listOf("nilüfer müslüm gürses", "nilüfer"), DesktopTrackMatcher.queries(target))
    }

    @Test
    fun exactSpellingRanksAheadOfFoldedTitleAndArtistMatches() {
        val target = song("Déjà Vu", "Beyoncé")
        val foldedTitle = song("Deja Vu", "Beyoncé")
        val foldedArtist = song("Déjà Vu", "Beyonce")
        val exact = target.copy(durationText = "3:45")

        assertEquals(
            listOf(exact, foldedArtist, foldedTitle),
            DesktopTrackMatcher.ranked(listOf(foldedTitle, foldedArtist, exact), target),
        )
    }

    @Test
    fun foldingRequiresArtistAgreementAndKeepsSpellingDifferencesApart() {
        val target = song("Café Noir", "Artist")
        val candidates = listOf(
            song("Cafe Noir", "Another Band"),
            song("Cafe Noir", "Unknown Artist"),
            song("Cafè Noir", "Artist"),
            song("CafeNoir", "Artist"),
            song("Cafe Bleu", "Artist"),
        )

        assertEquals(emptyList(), DesktopTrackMatcher.ranked(candidates, target))
        assertNull(DesktopTrackMatcher.best(listOf(song("Café Noir", "Beyoncè")), song("Café Noir", "Beyoncé")))
    }

    @Test
    fun nativeScriptAndItsCombiningMarksRemainSignificant() {
        val target = song("दिल", "कलाकार")

        assertEquals(target, DesktopTrackMatcher.best(listOf(target), target))
        assertNull(DesktopTrackMatcher.best(listOf(song("दल", "कलाकार")), target))
        assertNull(DesktopTrackMatcher.best(listOf(song("दिल", "कलकार")), target))
        assertNull(DesktopTrackMatcher.best(listOf(song("東京", "中")), song("東京", "山")))
    }

    @Test
    fun fullParenthesizedRomanizationMatchesInBothDirections() {
        val target = song("東京（Tōkyō） (Official Audio)", "Artist")
        val romanized = song("Tokyo", "Artist")
        val exact = song("東京", "Artist")

        assertEquals(romanized, DesktopTrackMatcher.best(listOf(romanized), target))
        assertEquals(target, DesktopTrackMatcher.best(listOf(target), romanized))
        assertEquals(exact, DesktopTrackMatcher.best(listOf(romanized, exact), target))
        assertEquals(
            listOf("東京 artist", "東京", "tōkyō artist", "tōkyō"),
            DesktopTrackMatcher.queries(target),
        )
    }

    @Test
    fun aliasesAndAccentFoldingKeepDurationAndVersionChecks() {
        val target = song("東京 (Tōkyō)", "Artist")
        val candidates = listOf(
            song("Tokyo (Live)", "Artist"),
            song("Tokyo", "Artist").copy(durationText = "4:31"),
            song("Tokyo", "Another Band"),
            song("Tokyo", "Unknown Artist"),
            song("京都 (Tōkyō)", "Artist"),
            song("Official Audio", "Artist"),
        )

        assertEquals(emptyList(), DesktopTrackMatcher.ranked(candidates, target))
        assertNull(DesktopTrackMatcher.best(listOf(song("Tokyo", "Artist")), song("曲 (From Tokyo)", "Artist")))
        assertNull(DesktopTrackMatcher.best(listOf(song("Night", "Artist")), song("夜 (Into the Night)", "Artist")))
        assertNull(DesktopTrackMatcher.best(listOf(song("Simarik (Live)", "Artist")), song("Şımarık", "Artist")))
    }

    private fun song(title: String, artist: String) =
        Song(title, title, artist, null, durationText = "4:00")
}
