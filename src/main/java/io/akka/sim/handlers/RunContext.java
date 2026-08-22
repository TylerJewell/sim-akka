package io.akka.sim.handlers;

import io.akka.sim.domain.BlockOutput;
import io.akka.sim.domain.Dag;
import io.akka.sim.domain.ReferenceResolver;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-run mutable context shared by every block handler: the outputs produced so far, indexed
 * both by block id (for internal lookups) and by normalized block name (for `<name.path>`
 * reference resolution, rule 6). Safe for concurrent writes from independently-executing blocks.
 */
public final class RunContext {
  public final Dag dag;
  public final AgentCaller agentCaller;
  private final Map<String, BlockOutput> outputsById = new ConcurrentHashMap<>();
  private final Map<String, BlockOutput> outputsByNormalizedName = new ConcurrentHashMap<>();

  public RunContext(Dag dag, AgentCaller agentCaller) {
    this.dag = dag;
    this.agentCaller = agentCaller;
  }

  public void recordOutput(String blockId, BlockOutput output) {
    outputsById.put(blockId, output);
    var node = dag.nodes.get(blockId);
    if (node != null) {
      outputsByNormalizedName.put(ReferenceResolver.normalizeName(node.name), output);
    }
  }

  public String resolve(String text) {
    return ReferenceResolver.resolve(text, outputsByNormalizedName);
  }
}
