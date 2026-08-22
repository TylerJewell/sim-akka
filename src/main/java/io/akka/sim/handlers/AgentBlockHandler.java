package io.akka.sim.handlers;

import io.akka.sim.domain.BlockConfig;
import io.akka.sim.domain.BlockOutput;
import io.akka.sim.domain.DagNode;
import java.util.Map;

/**
 * Resolves the prompt template's {@code <name.path>} references (rule 6) against prior outputs,
 * then calls the run's {@link AgentCaller} boundary (SPEC-001 §4) in place of a live provider.
 */
public final class AgentBlockHandler implements BlockHandler {
  @Override
  public BlockOutput execute(RunContext ctx, DagNode node) {
    var config = (BlockConfig.Agent) node.config;
    var resolvedPrompt = ctx.resolve(config.promptTemplate());
    var response = ctx.agentCaller.call(resolvedPrompt);
    return BlockOutput.of(Map.of("prompt", resolvedPrompt, "response", response));
  }
}
