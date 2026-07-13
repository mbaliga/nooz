package xyz.mdhv.riverwip.feature.reader

import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import xyz.mdhv.riverwip.design.DisplayFontFamily
import xyz.mdhv.riverwip.design.SectionHeading
import xyz.mdhv.riverwip.design.Tokens
import xyz.mdhv.riverwip.inference.Provenance
import java.util.Locale

/**
 * Nooz Flash (owner's #6, then owner's follow-up "it's unfindable"): today's
 * flowed headlines, compressed to 10 words or fewer. A bolt mark and a
 * bordered card give it a distinct silhouette on the Stand — plain muted
 * body text read as ordinary chrome, easy to scroll straight past. Never
 * fetched automatically — the reader taps for it, same "explicit consent"
 * contract every other generation in this app follows. "Go deeper" doesn't
 * call the model a second time; it just reveals the real headline list the
 * flash line was compressed from, so the honest source is always one tap
 * away, never a second, ungrounded generation. "Play" reads the flash line
 * aloud via the device's own on-device text-to-speech engine — no network,
 * matching the feature's on-device-first stance.
 */
@Composable
fun FlashCard(vm: ReaderViewModel, modifier: Modifier = Modifier) {
    val state by vm.flashState.collectAsStateWithLifecycle()
    var expanded by remember { mutableStateOf(false) }

    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Tokens.Radius.md))
            .border(BorderStroke(Tokens.Border.thin, MaterialTheme.colorScheme.outlineVariant), RoundedCornerShape(Tokens.Radius.md))
            .padding(Tokens.Spacing.sm),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Tokens.Spacing.xs)) {
            Icon(
                Icons.Filled.FlashOn,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.size(18.dp),
            )
            SectionHeading("Nooz Flash", color = MaterialTheme.colorScheme.onBackground)
        }
        when (val s = state) {
            is FlashUiState.Idle -> Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClickLabel = "Compress today's news") { vm.requestFlash() }
                    .padding(top = Tokens.Spacing.xxs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "Tap to compress today's news to 10 words or fewer",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
            }
            is FlashUiState.Loading -> Row(
                modifier = Modifier.padding(top = Tokens.Spacing.xs),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Tokens.Spacing.sm),
            ) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                Text(
                    "Compressing today's news…",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            is FlashUiState.Ready -> {
                Text(
                    s.flash,
                    style = MaterialTheme.typography.headlineSmall.copy(fontFamily = DisplayFontFamily),
                    modifier = Modifier.padding(top = Tokens.Spacing.xxs),
                )
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Tokens.Spacing.sm)) {
                    val provenanceLabel = if (s.provenance == Provenance.NATIVE) "on-device" else "cloud (your key)"
                    val provenanceColor = if (s.provenance == Provenance.NATIVE) Tokens.Color.provenanceNative else Tokens.Color.provenanceCloud
                    Text(provenanceLabel, style = MaterialTheme.typography.labelSmall, color = provenanceColor)
                    Text(
                        if (expanded) "Hide the headlines" else "Go deeper",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.clickable { expanded = !expanded },
                    )
                    Spacer(Modifier.weight(1f))
                    PlayFlashButton(text = s.flash)
                }
                AnimatedVisibility(expanded) {
                    Column(Modifier.padding(top = Tokens.Spacing.xs)) {
                        for (headline in s.headlines) {
                            Text(
                                headline,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(vertical = Tokens.Spacing.xxs),
                            )
                        }
                    }
                }
            }
            is FlashUiState.Unavailable -> Text(
                "Nooz Flash: ${s.reason}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = Tokens.Spacing.xxs),
            )
        }
    }
}

/**
 * Reads [text] aloud on tap via the device's own text-to-speech engine —
 * entirely on-device, no network, matching Nooz Flash's own stance. Tapping
 * again while speaking stops it early.
 */
@Composable
private fun PlayFlashButton(text: String) {
    val context = LocalContext.current
    var engine by remember { mutableStateOf<TextToSpeech?>(null) }
    var speaking by remember { mutableStateOf(false) }

    DisposableEffect(context) {
        val instance = TextToSpeech(context) { }
        instance.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) { speaking = true }
            override fun onDone(utteranceId: String?) { speaking = false }
            override fun onError(utteranceId: String?) { speaking = false }
        })
        engine = instance
        onDispose {
            instance.stop()
            instance.shutdown()
            engine = null
        }
    }

    IconButton(
        onClick = {
            val tts = engine ?: return@IconButton
            if (speaking) {
                tts.stop()
                speaking = false
            } else {
                tts.setLanguage(Locale.getDefault())
                tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "nooz-flash")
            }
        },
    ) {
        Icon(
            if (speaking) Icons.Filled.Stop else Icons.Filled.PlayArrow,
            contentDescription = if (speaking) "Stop reading aloud" else "Read the flash aloud",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
