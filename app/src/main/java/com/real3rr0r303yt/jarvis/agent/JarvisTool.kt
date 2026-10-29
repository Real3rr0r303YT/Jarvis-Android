package com.real3rr0r303yt.jarvis.agent

/**
 * A capability the agent can invoke during its ReAct loop. Each tool has a
 * stable [name] the planner references, a human-readable [description] that is
 * injected into the planning prompt, and a suspending [run] that performs the
 * work and returns a short textual observation for the loop to reason over.
 */
interface JarvisTool {
    val name: String
    val description: String

    /** Executes the tool with the planner-supplied [input]; returns an observation. */
    suspend fun run(input: String): String
}

/** One decoded step of the planner's output. */
sealed interface AgentStep {
    /** The model's reasoning for this turn. */
    data class Thought(val text: String) : AgentStep

    /** The model wants to call [tool] with [input]. */
    data class Action(val tool: String, val input: String) : AgentStep

    /** The model is done; [answer] is the final response to the user. */
    data class Final(val answer: String) : AgentStep
}
