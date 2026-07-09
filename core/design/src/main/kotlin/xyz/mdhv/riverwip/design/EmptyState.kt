package xyz.mdhv.riverwip.design

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview

/**
 * A quiet empty state. Empty states explain the honest denominator (brief §1):
 * this app never claims "all the news" — the denominator is always the user's
 * declared source-set.
 */
@Composable
fun EmptyState(
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(Tokens.Spacing.xl),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
        )
        Text(
            text = body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = Tokens.Spacing.xs),
        )
        if (action != null) {
            Column(Modifier.padding(top = Tokens.Spacing.md)) { action() }
        }
    }
}

@Preview(backgroundColor = 0xFF121212, showBackground = true)
@Composable
private fun EmptyStatePreview() {
    RiverTheme(dark = true) {
        EmptyState(
            title = "No sources yet",
            body = "This reader only shows ${Copy.fromSources(0)}. Add a feed to begin — " +
                "it never claims to show all the news.",
        )
    }
}
