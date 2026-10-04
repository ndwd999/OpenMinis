package com.yujian.minis.data

import com.yujian.minis.ProductionSources
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Which provider rejections Android recognises as "the request has too many
 * tokens", and what the loop does with the valve after one.
 *
 * Guards:
 *   - 9aad0ffb5 / df35445ee  T-ctx-measure-outbound — noteContextOverflow raises
 *     the calibration ratio, but only for errors [ContextOverflowGuard] accepts;
 *   - 6a3f9e0da  T-ctx-overflow-no-cross-fallback — a direct-entry session must
 *     not fall back to another vendor on an overflow, which ALSO keys off
 *     [ContextOverflowGuard];
 *   - 0e0a8b339  T-ctx-valve-rearm — "a rejection spends the valve".
 *
 * Every miss on Android means: no ratio raise, no GH#352 self-heal, and —
 * because a ProviderError is `isFallbackable` — a silent answer from a
 * different model on a session pinned to one.
 *
 * This file also used to diff the marker list against iOS
 * (`ContextSizeMeter.isContextOverflow` in ContextPolicy.swift) to catch
 * wording that only the other platform recognised. That comparison went away
 * with `src/ios` in the Android-only trim; the wording cases it used to supply
 * are spelled out literally in the individual tests below instead, so a
 * regression still fails here rather than silently passing.
 *
 * Tests marked EXPECTED TO FAIL document bugs found in review; they stay red
 * until production is fixed.
 */
class ContextOverflowRejectionParityTest {

    /**
     * The wordings the iOS marker list used to supply. Kept as literals so the
     * exact rejections iOS accepted stay covered after the parity diff is gone.
     */
    private val overflowWordings = listOf(
        "prompt is too long",
        "exceeds the context window",
        "input exceeds the context",
    )

    /** EXPECTED TO FAIL until fixed: the last two wordings came from iOS only. */
    @Test
    fun `every known overflow wording is an overflow on Android`() {
        val missing = overflowWordings.filterNot { ContextOverflowGuard.isContextOverflow(400, "[400] $it") }
        assertEquals("overflow wordings Android does not recognise", emptyList<String>(), missing)
    }

    /**
     * OpenMinis#133 over HTTP. OpenAIProvider.mapHttpError keeps only
     * `error.message`, so the `context_length_exceeded` code never reaches the
     * detail. EXPECTED TO FAIL until fixed.
     */
    @Test
    fun `OpenMinis 133 wording on an HTTP 400 is an overflow`() {
        val openAi = ProductionSources.read("provider/openai/OpenAIProvider.kt")
        assertTrue(
            "mapHttpError format changed — update this test",
            openAi.contains("\"[\$statusCode] \$errorMessage\""),
        )
        assertTrue(
            ContextOverflowGuard.isContextOverflow(
                400,
                "[400] Your input exceeds the context window of this model. Please adjust your input and try again.",
            ),
        )
    }

    /**
     * The same rejection arriving as a Responses `response.failed` event.
     * OpenAIProvider throws a ProviderError of "code + message" WITHOUT an
     * httpStatus, and the guard refuses any status-less error — so a
     * structured `context_length_exceeded` code is ignored. EXPECTED TO FAIL
     * until the provider passes a status for it (or the guard accepts that code).
     */
    @Test
    fun `a Responses response_failed context_length_exceeded is an overflow`() {
        val openAi = ProductionSources.read("provider/openai/OpenAIProvider.kt")
        val statusLess = openAi.contains("throw LLMError.ProviderError(\"[\$code] \$message\")\n")
        val status: Int? = if (statusLess) null else 400
        assertTrue(
            "response.failed context_length_exceeded (status=$status) is not recognised",
            ContextOverflowGuard.isContextOverflow(
                status,
                "[context_length_exceeded] Your input exceeds the context window of this model.",
            ),
        )
    }

    /**
     * Anthropic's input + max_tokens rejection matches no marker on either
     * platform. EXPECTED TO FAIL until fixed.
     */
    @Test
    fun `Anthropic input plus max_tokens over the limit is an overflow`() {
        assertTrue(
            ContextOverflowGuard.isContextOverflow(
                400,
                "[invalid_request_error] input length and `max_tokens` exceed context limit: " +
                    "188240 + 21333 > 200000, decrease input length or `max_tokens` and try again",
            ),
        )
    }

    @Test
    fun `a per-minute token rate limit is still not an overflow`() {
        assertFalse(ContextOverflowGuard.isContextOverflow(429, "too many tokens per minute"))
    }

    /**
     * 0e0a8b339 made noteContextOverflow set sentPastExtrapolatedLimitThisLoop
     * so the valve cannot re-send a request the provider just rejected. When
     * the GH#352 self-heal finds nothing to offload, the rejection ends the
     * loop, and runAgentLoop's prologue resets the flag — so the retry
     * compacts, makes no progress, and fires the valve on the same request.
     * (On iOS the rejection ALWAYS ends the loop first, so there the fix is
     * never effective.) EXPECTED TO FAIL until the prologue stops discarding
     * a rejection's spend.
     */
    @Test
    fun `the loop prologue does not discard a rejection's spent valve`() {
        val vm = ProductionSources.read("ui/chat/ChatViewModel.kt")
        assertTrue(
            "noteContextOverflow no longer spends the valve — update this test",
            vm.contains("sentPastExtrapolatedLimitThisLoop = true\n        AppLogger.warning("),
        )
        val prologueReset = vm.contains(
            "lastInLoopCompactionMadeNoProgress = false\n        sentPastExtrapolatedLimitThisLoop = false",
        )
        assertFalse("runAgentLoop's prologue unconditionally re-arms the valve", prologueReset)
    }

    /** What the valve does on that retry, through the production decision table. */
    @Test
    fun `after a rejection the retry stops rather than re-sending`() {
        val window = 128_000
        val raw = 120_000
        val ratio = ContextSizeMeter.ratioAfterOverflow(1.0, raw, 156_000, window)
        val measured = ContextSizeMeter.calibrated(raw, ratio)
        val step = ContextPolicy.inLoopStep(
            verdict = ContextPolicy.CheckResult.NEEDS_COMPACT,
            measured = measured,
            rawTokens = raw,
            window = window,
            canCompact = false,
            ratio = ratio,
            uncalibratedSendUsed = true, // the rejection spent it
        )
        assertEquals(ContextPolicy.InLoopStep.STOP, step)
    }
}
