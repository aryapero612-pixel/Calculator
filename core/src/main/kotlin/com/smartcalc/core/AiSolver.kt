package com.smartcalc.core

import com.anthropic.client.AnthropicClient
import com.anthropic.client.okhttp.AnthropicOkHttpClient
import com.anthropic.core.JsonValue
import com.anthropic.errors.AnthropicServiceException
import com.anthropic.errors.AnthropicIoException
import com.anthropic.errors.RateLimitException
import com.anthropic.errors.UnauthorizedException
import com.anthropic.models.messages.MessageCreateParams
import com.anthropic.models.messages.OutputConfig
import com.anthropic.models.messages.StopReason

/**
 * Claude-powered solver for word problems and anything the offline engine can't parse.
 * Uses the official Anthropic Java SDK. The user supplies their own API key in Settings.
 */
class AiSolver(apiKey: String, private val model: String = DEFAULT_MODEL) {

    private val client: AnthropicClient = AnthropicOkHttpClient.builder()
        .apiKey(apiKey)
        .build()

    /** Blocking call — run it off the main thread. */
    fun solve(question: String): String {
        val params = MessageCreateParams.builder()
            .model(model)
            .maxTokens(16000L)
            .system(SYSTEM_PROMPT)
            .outputConfig(OutputConfig.builder().effort(OutputConfig.Effort.MEDIUM).build())
            // Server-side refusal fallback: if the model declines, the API retries on a suitable model.
            .putAdditionalHeader("anthropic-beta", "server-side-fallback-2026-07-01")
            .putAdditionalBodyProperty("fallbacks", JsonValue.from("default"))
            .addUserMessage(question)
            .build()

        return try {
            val response = client.messages().create(params)
            if (response.stopReason().orElse(null) == StopReason.REFUSAL) {
                return "Sorry, I can't help with that request."
            }
            val text = response.content()
                .mapNotNull { block -> block.text().orElse(null)?.text() }
                .joinToString("\n")
                .trim()
            text.ifEmpty { "No answer was returned. Please try rephrasing." }
        } catch (e: UnauthorizedException) {
            throw CalcException("Invalid API key. Check it in Settings.")
        } catch (e: RateLimitException) {
            throw CalcException("Too many requests — please wait a moment and try again.")
        } catch (e: AnthropicServiceException) {
            throw CalcException("AI service error (${e.statusCode()}). Please try again.")
        } catch (e: AnthropicIoException) {
            throw CalcException("Network error — check your internet connection.")
        }
    }

    fun close() = client.close()

    companion object {
        const val DEFAULT_MODEL = "claude-opus-5-5"

        private val SYSTEM_PROMPT = """
            You are the math assistant inside SmartCalc, a calculator app on a phone screen.
            Solve the user's math, finance, unit or word problem.
            Reply in the same language the user writes in (for example Hindi, Hinglish or English).
            Format: short numbered steps in plain text (no LaTeX, no markdown tables), then a final
            line that starts with "Answer:" followed by the result with units.
            Keep it concise enough to read on a small screen. If the question is not about
            mathematics, numbers or calculations, say briefly that you can only help with calculations.
        """.trimIndent()
    }
}
