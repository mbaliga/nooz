package xyz.mdhv.riverwip

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import xyz.mdhv.riverwip.design.Tokens

/**
 * "What's inside" — the short tour of the things a reader will otherwise never
 * find.
 *
 * Nooz hides almost nothing behind a menu: the Loom opens by *pulling down on
 * the stand*, Clippings by a bookmark inside the reader, and Flash, Cast and
 * Today in History are all deliberately **off** until switched on (each is
 * either a standing generation over everything that flowed, or the one fetch
 * this app makes to somewhere the reader didn't add). Every one of those is a
 * defensible design decision on its own, and together they add up to a set of
 * features nobody discovers. This is the counterweight: five lines, each
 * naming a thing and saying where it lives.
 *
 * Deliberately **not** a set of toggles. Flash and Cast each pull down a model,
 * and "off until you decide" is the whole point of how they ship — flipping
 * that decision on a reader's behalf during setup, before the words mean
 * anything to them, would trade one bad outcome for a worse one. So the tour
 * points at the switch instead of being the switch.
 *
 * Shared on purpose: onboarding shows it once as a send-off, and Settings
 * keeps it permanently, because every reader who onboarded before this existed
 * would otherwise never see it at all.
 */
private data class TourEntry(val name: String, val body: String)

private val TOUR_ENTRIES = listOf(
    TourEntry(
        "The Loom",
        "What your sources actually sent you, woven against what you actually read. " +
            "Pull down on your stand, or tap the coloured bar above the stories.",
    ),
    TourEntry(
        "Nooz Flash",
        "The day's headlines cut to a line each, with a way back into the full piece. " +
            "Off until you switch it on, in Settings.",
    ),
    TourEntry(
        "Nooz Cast",
        "An article read aloud in a voice that runs on your phone rather than a server. " +
            "Also in Settings; it asks before downloading anything.",
    ),
    TourEntry(
        "Clippings",
        "Tap the bookmark while reading to keep a piece. Clippings holds them as torn scraps of the paper.",
    ),
    TourEntry(
        "Today in History",
        "A short dated column above the day's stories, from Wikipedia's own \"on this day\". Off until you switch it on.",
    ),
)

/**
 * The tour body, with no framing of its own, so the host decides the heading,
 * the buttons, and whether it sits in a card or a settings list.
 */
@Composable
fun FeatureTourContent(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(Tokens.Spacing.sm),
    ) {
        for (entry in TOUR_ENTRIES) {
            Column(verticalArrangement = Arrangement.spacedBy(Tokens.Spacing.xxs)) {
                Text(
                    entry.name,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Text(
                    entry.body,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Text(
            "Nothing here reaches for anything you didn't ask for. The three switches above stay off until you turn them on.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
