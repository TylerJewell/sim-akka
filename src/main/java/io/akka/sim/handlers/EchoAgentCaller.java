package io.akka.sim.handlers;

/**
 * Default {@link AgentCaller}: echoes the resolved prompt back as the response. Deterministic and
 * network-free, so the DAG engine and run monitoring are exercisable without provider credentials
 * (SPEC-001 §4).
 */
public final class EchoAgentCaller implements AgentCaller {
  @Override
  public String call(String resolvedPrompt) {
    return resolvedPrompt;
  }
}
