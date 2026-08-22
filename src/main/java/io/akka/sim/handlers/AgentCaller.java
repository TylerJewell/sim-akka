package io.akka.sim.handlers;

/**
 * The port's boundary in place of the source's live LLM provider call (SPEC-001 §4,
 * question-log row 9). Given the resolved prompt, produces the agent block's response text.
 */
public interface AgentCaller {
  String call(String resolvedPrompt);
}
