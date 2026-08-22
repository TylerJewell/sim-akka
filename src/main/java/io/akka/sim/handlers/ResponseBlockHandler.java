package io.akka.sim.handlers;

import io.akka.sim.domain.BlockOutput;
import io.akka.sim.domain.DagNode;
import java.util.Map;

/** Terminal block. The engine treats its completion as the run's end — SPEC-001 §3 rule 4. */
public final class ResponseBlockHandler implements BlockHandler {
  @Override
  public BlockOutput execute(RunContext ctx, DagNode node) {
    return BlockOutput.of(Map.of());
  }
}
