package com.real3rr0r303yt.jarvis.agent

import com.real3rr0r303yt.jarvis.agent.llm.LlmClient
import com.real3rr0r303yt.jarvis.core.JarvisLog

/**
 * Autonomous ReAct planner: given a user goal, it loops
 * Thought -> Action -> Observation until it produces a Final Answer, driving
 * the registered [tools] through the configured [llm].
 *
 * Each step is streamed to the HUD terminal:
 *   AGENT THINKING: ...
 *   AGENT ACTION: <tool>(...)
 *   AGENT RESULT: <observation>
 *
 * The loop is bounded by [maxSteps] so a confused model cannot spin forever.
 */
class JarvisAgentEngine(
    private val llm: LlmClient,
    toolList: List<JarvisTool>,
    private val maxSteps: Int = 6,
) {
    private val tools: Map<String, JarvisTool> = toolList.associateBy { it.name }

    private val systemPrompt: String = buildString {
        appendLine("You are Jarvis, an autonomous Android agent. Solve the user's goal by")
        appendLine("reasoning step by step and calling tools. You have these tools:")
        appendLine()
        toolList.forEach { appendLine("- ${it.name}: ${it.description}") }
        appendLine()
        appendLine("On each turn reply in EXACTLY one of these two formats:")
        appendLine()
        appendLine("Thought: <your reasoning>")
        appendLine("Action: <one tool name from the list>")
        appendLine("Action Input: <the input string for that tool>")
        appendLine()
        appendLine("or, when you have enough information:")
        appendLine()
        appendLine("Thought: <your reasoning>")
        appendLine("Final Answer: <the answer or confirmation for the user>")
        appendLine()
        appendLine("Use one Action per turn. Keep inputs short and concrete.")
    }

    /** Runs the loop for [goal]; returns the final answer (or a bounded-out note). */
    suspend fun run(goal: String): String {
        val scratchpad = StringBuilder("Goal: ").append(goal).append('\n')

        for (step in 1..maxSteps) {
            val raw = try {
                llm.complete(systemPrompt, scratchpad.toString())
            } catch (e: Exception) {
                JarvisLog.error("AGENT planner error: ${e.message}")
                return "Agent stopped: planner error (${e.message})."
            }

            when (val parsed = parse(raw)) {
                is AgentStep.Final -> {
                    JarvisLog.action("AGENT DONE: ${parsed.answer.take(160)}")
                    return parsed.answer
                }

                is AgentStep.Action -> {
                    val thought = extractThought(raw)
                    if (thought.isNotBlank()) JarvisLog.info("AGENT THINKING: ${thought.take(160)}")
                    JarvisLog.action("AGENT ACTION: ${parsed.tool}(\"${parsed.input.take(80)}\")")

                    val tool = tools[parsed.tool]
                    val observation = if (tool == null) {
                        "Error: unknown tool '${parsed.tool}'. Pick one of ${tools.keys}."
                    } else {
                        runCatching { tool.run(parsed.input) }
                            .getOrElse { "Error running ${parsed.tool}: ${it.message}" }
                    }
                    JarvisLog.info("AGENT RESULT: ${observation.take(200)}")

                    scratchpad.append(raw.trim()).append('\n')
                        .append("Observation: ").append(observation).append('\n')
                }

                is AgentStep.Thought -> {
                    // Model reasoned but named no action; nudge it to act or finish.
                    JarvisLog.info("AGENT THINKING: ${parsed.text.take(160)}")
                    scratchpad.append(raw.trim()).append('\n')
                        .append("Observation: No action was taken. Call a tool or give a Final Answer.\n")
                }
            }
        }

        JarvisLog.warn("AGENT stopped: reached step limit ($maxSteps)")
        return "Agent reached its step limit without a final answer."
    }

    // ------------------------------------------------------------- parsing
    private fun parse(raw: String): AgentStep {
        val final = Regex("Final Answer:\\s*(.+)", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
            .find(raw)
        if (final != null) return AgentStep.Final(final.groupValues[1].trim())

        val action = Regex("Action:\\s*(.+)", RegexOption.IGNORE_CASE).find(raw)?.groupValues?.get(1)?.trim()
        val actionInput = Regex("Action Input:\\s*(.+)", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
            .find(raw)?.groupValues?.get(1)?.trim()
        if (!action.isNullOrBlank()) {
            return AgentStep.Action(tool = action.substringBefore('\n').trim(), input = actionInput.orEmpty())
        }
        return AgentStep.Thought(extractThought(raw).ifBlank { raw.trim() })
    }

    private fun extractThought(raw: String): String =
        Regex("Thought:\\s*(.+)", RegexOption.IGNORE_CASE).find(raw)?.groupValues?.get(1)?.substringBefore('\n')?.trim().orEmpty()
}
