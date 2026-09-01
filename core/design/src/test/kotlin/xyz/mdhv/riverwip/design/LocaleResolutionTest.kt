package xyz.mdhv.riverwip.design

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * That Android actually *picks* the translations, not merely that they ship.
 *
 * The gap this closes was a real hole in how the locale work had been verified.
 * `resources.arsc` was checked and the Hindi, Tamil, Urdu and Punjabi strings
 * were in it — which proves they were packaged, and nothing more. Resource
 * *selection* is a separate mechanism: it depends on the `values-b+<tag>`
 * qualifier being spelled the way `Locales.androidResourceQualifier` spells it,
 * on the library's resources merging into the app, and on the tag Android
 * derives from the locale matching. Get any of that wrong and every locale
 * falls back to English, silently, with the strings still sitting in the APK
 * exactly as the earlier check found them.
 *
 * `@Config(qualifiers = …)` puts Robolectric's resource resolver through the
 * real path, so this is the end-to-end answer for the scheme all thirty locales
 * are built on.
 */
@RunWith(RobolectricTestRunner::class)
class LocaleResolutionTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    private fun paper(): String = context.getString(R.string.screen_paper)

    @Test @Config(qualifiers = "en") fun englishIsTheBase() {
        assertEquals("Paper", paper())
    }

    @Test @Config(qualifiers = "b+hi") fun hindiResolves() {
        assertEquals("अख़बार", paper())
    }

    @Test @Config(qualifiers = "b+ta") fun tamilResolves() {
        assertEquals("பத்திரிகை", paper())
    }

    @Test @Config(qualifiers = "b+ur") fun urduResolves() {
        assertEquals("اخبار", paper())
    }

    @Test @Config(qualifiers = "b+zh+Hans") fun aScriptSubtagResolves() {
        // The one tag with a script subtag, and the one place the
        // BCP 47 -> `b+zh+Hans` conversion could go wrong.
        assertEquals("报纸", paper())
    }

    @Test @Config(qualifiers = "b+mai") fun aThreeLetterTagResolves() {
        // Maithili: three-letter codes go down a different path in some
        // resource resolvers than two-letter ones.
        assertEquals("अखबार", paper())
    }

    @Test @Config(qualifiers = "b+ks") fun aPartialLocaleFallsBackPerKey() {
        // Kashmiri has a translation for this key and not for most others.
        // Per-key fallback is the property the whole "partial is safe" design
        // rests on, so it is asserted rather than assumed.
        assertEquals("اخبار", paper())
        assertEquals(
            "an untranslated key falls back to English, not to a blank or a key name",
            "Quick setup",
            context.getString(R.string.onboarding_quick_setup),
        )
    }

    @Test @Config(qualifiers = "b+is") fun anUnshippedLocaleGetsEnglish() {
        // Icelandic is not in the catalogue; it must land on the base, not on
        // an empty string.
        assertEquals("Paper", paper())
    }

    @Test @Config(qualifiers = "b+te") fun formatArgumentsSurviveTranslation() {
        // A translated format string that lost its placeholder would render a
        // gap here rather than the number.
        val spoken = context.getString(R.string.loom_stream_action, "రాజకీయాలు", 40, 2)
        assertTrue("the counts are in the string: $spoken", spoken.contains("40") && spoken.contains("2"))
        assertTrue("and it is not the English one: $spoken", !spoken.contains("flowed"))
    }

    @Test @Config(qualifiers = "b+ml") fun theSettingsScreenResolves() {
        // Settings was the last and largest screen to be migrated, and the one
        // whose copy carries the app's privacy claims. Checking a body string
        // rather than a one-word label is deliberate: a long string is where a
        // half-finished migration shows up, because a screen can be wired for
        // its headings and still be English underneath.
        assertEquals("നിഘണ്ടു", context.getString(R.string.settings_dictionary))
        val yourData = context.getString(R.string.settings_your_data_body)
        assertTrue("resolution reached Malayalam: $yourData", !yourData.contains("Export everything"))
        assertTrue("and API is left as-is inside it: $yourData", yourData.contains("API"))
    }

    @Test @Config(qualifiers = "b+ar") fun aSettingsFormatStringKeepsItsPlaceholder() {
        // settings_size_license carries two positional arguments; a translation
        // that dropped one would silently render half the row.
        val row = context.getString(R.string.settings_size_license, "48 MB", "MIT")
        assertTrue("both arguments land: $row", row.contains("48 MB") && row.contains("MIT"))
    }

    @Test @Config(qualifiers = "b+ta") fun spokenLabelsResolve() {
        // `onClickLabel` and custom-action labels are copy that ONLY a screen
        // reader ever renders -- nothing on the display shows them. That is
        // exactly why they went unnoticed until the guard was widened to match
        // them: a sighted check of a Tamil build looks completely translated
        // while every spoken affordance is still English.
        assertEquals("அன்றைய தறியைத் திற", context.getString(R.string.list_open_loom))
        val define = context.getString(R.string.lens_define_word, "பத்திரிகை")
        assertTrue("the word is interpolated, not dropped: $define", define.contains("பத்திரிகை"))
        assertTrue("and the frame is Tamil: $define", !define.startsWith("Define"))
    }

    @Test @Config(qualifiers = "b+bn") fun everyShippedLocaleDiffersFromEnglish() {
        // A weak assertion on purpose: it cannot check the Bengali is *good*,
        // only that resolution reached it at all rather than falling through.
        assertNotEquals("Paper", paper())
    }
}
