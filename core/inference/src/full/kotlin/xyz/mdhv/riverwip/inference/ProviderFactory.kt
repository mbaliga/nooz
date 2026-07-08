package xyz.mdhv.riverwip.inference

import android.content.Context
import java.io.File
import xyz.mdhv.riverwip.inference.local.LocalLlamaProvider
import xyz.mdhv.riverwip.inference.mlkit.MlKitProvider
import xyz.mdhv.riverwip.inference.urbana.UrbanaProvider

/**
 * `full`-flavor provider order (brief §5: Urbana → local llama.cpp → ML Kit) —
 * adds [MlKitProvider] after the two `foss`-safe providers. See the `foss`
 * sibling file for how the flavor resolution works.
 */
object ProviderFactory {
    fun build(context: Context, modelDir: File): List<InferenceProvider> = listOf(
        UrbanaProvider(context),
        LocalLlamaProvider(modelDir),
        MlKitProvider(),
    )
}
