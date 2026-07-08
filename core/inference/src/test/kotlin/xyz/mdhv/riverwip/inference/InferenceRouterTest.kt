package xyz.mdhv.riverwip.inference

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private class FakeProvider(
    override val id: String,
    private val available: Boolean,
    private val result: RewriteResult = RewriteResult.Failed("not configured"),
) : InferenceProvider {
    var rewriteCalls = 0
        private set
    override suspend fun isAvailable(): Boolean = available
    override suspend fun rewrite(request: RewriteRequest): RewriteResult {
        rewriteCalls++
        return result
    }
}

class InferenceRouterTest {

    private val req = RewriteRequest("The minister slammed the bill.", "slammed", 15, 22)

    @Test fun triesProvidersInOrderAndUsesFirstAvailable() = runTest {
        val urbana = FakeProvider("urbana", available = false)
        val local = FakeProvider("local-llama", available = true, result = RewriteResult.Success("The minister criticized the bill.", Provenance.NATIVE))
        val mlkit = FakeProvider("mlkit", available = true, result = RewriteResult.Success("wrong one", Provenance.NATIVE))
        val router = InferenceRouter(listOf(urbana, local, mlkit))

        val result = router.rewrite(req)
        assertTrue(result is RewriteResult.Success)
        assertEquals("The minister criticized the bill.", (result as RewriteResult.Success).rewrittenSentence)
        assertEquals(0, urbana.rewriteCalls) // unavailable — skipped, never called
        assertEquals(1, local.rewriteCalls)
        assertEquals(0, mlkit.rewriteCalls) // never reached — local already answered
    }

    @Test fun stopsAtFirstAvailableProviderEvenIfItFails() = runTest {
        // A provider that IS available but fails should not fall through to the
        // next one — total inspectability means the user sees *why* it failed,
        // not a silently-masked retry.
        val local = FakeProvider("local-llama", available = true, result = RewriteResult.Failed("model not downloaded"))
        val mlkit = FakeProvider("mlkit", available = true, result = RewriteResult.Success("should not be used", Provenance.NATIVE))
        val router = InferenceRouter(listOf(local, mlkit))

        val result = router.rewrite(req)
        assertTrue(result is RewriteResult.Failed)
        assertEquals("model not downloaded", (result as RewriteResult.Failed).reason)
        assertEquals(0, mlkit.rewriteCalls)
    }

    @Test fun allUnavailableReportsWhichWereTried() = runTest {
        val router = InferenceRouter(listOf(FakeProvider("urbana", false), FakeProvider("local-llama", false)))
        val result = router.rewrite(req)
        assertTrue(result is RewriteResult.Failed)
        val reason = (result as RewriteResult.Failed).reason
        assertTrue(reason.contains("urbana"))
        assertTrue(reason.contains("local-llama"))
    }

    @Test fun emptyOrderFailsImmediately() = runTest {
        val result = InferenceRouter(emptyList()).rewrite(req)
        assertTrue(result is RewriteResult.Failed)
    }

    @Test fun providerOrderReflectsConfiguredSequence() {
        val router = InferenceRouter(listOf(FakeProvider("urbana", true), FakeProvider("local-llama", true)))
        assertEquals(listOf("urbana", "local-llama"), router.providerOrder)
    }
}
