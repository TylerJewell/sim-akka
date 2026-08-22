package io.akka.sim.handlers;

import io.akka.sim.domain.BlockConfig;
import io.akka.sim.domain.BlockOutput;
import io.akka.sim.domain.DagEdge;
import io.akka.sim.domain.DagNode;
import java.util.Map;

/**
 * Resolves {@code selectorRef} to a literal value that becomes {@code selectedRoute}. Open
 * decision (SPEC-001 §4, question-log row 12): a value matching no outgoing {@code router-<id>}
 * edge is a run error, not a silently dead path — the source's dead-end cascade this would
 * otherwise fall into depends on loop/parallel sentinels this port does not have (§1).
 */
public final class RouterBlockHandler implements BlockHandler {
  private static final String ROUTER_PREFIX = "router-";

  @Override
  public BlockOutput execute(RunContext ctx, DagNode node) {
    var config = (BlockConfig.Router) node.config;
    var route = ctx.resolve(config.selectorRef());

    boolean hasMatchingEdge = node.outgoingEdges.stream()
        .map(DagEdge::sourceHandle)
        .anyMatch(handle -> handle != null && handle.equals(ROUTER_PREFIX + route));

    if (!hasMatchingEdge) {
      return BlockOutput.failed("router selected route '" + route + "' with no matching outgoing edge");
    }
    return BlockOutput.routed(Map.of("selected", route), route);
  }
}
