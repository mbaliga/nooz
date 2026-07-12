package xyz.mdhv.riverwip.inference

/**
 * User-ordered fallback across providers (brief §5, default order: Urbana →
 * local llama.cpp → ML Kit). Tries each provider in [order] until one is
 * available and returns a result; a provider that reports unavailable is
 * skipped silently (that's its contract), but a provider that *fails* while
 * attempting a rewrite stops the chain and surfaces the failure rather than
 * masking it by falling through — total inspectability (brief §3) means the
 * user sees why a rewrite didn't happen, not just that it didn't.
 */
class InferenceRouter(private val order: List<InferenceProvider>) {

    /** The order the router will actually try, i.e. [order] as given (user-configurable upstream). */
    val providerOrder: List<String> get() = order.map { it.id }

    suspend fun rewrite(request: RewriteRequest): RewriteResult {
        if (order.isEmpty()) return RewriteResult.Failed("no inference provider configured")
        for (provider in order) {
            if (!provider.isAvailable()) continue
            return provider.rewrite(request)
        }
        return RewriteResult.Failed("no available inference provider (tried: ${order.joinToString { it.id }})")
    }

    suspend fun digest(request: DigestRequest): DigestResult {
        if (order.isEmpty()) return DigestResult.Failed("no inference provider configured")
        for (provider in order) {
            if (!provider.isAvailable()) continue
            return provider.digest(request)
        }
        return DigestResult.Failed("no available inference provider (tried: ${order.joinToString { it.id }})")
    }
}
