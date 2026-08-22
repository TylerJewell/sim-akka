package io.akka.sim.handlers;

import io.akka.sim.domain.BlockOutput;
import io.akka.sim.domain.DagNode;
import java.util.Map;

/** The workflow's entry point. Produces an empty output; downstream blocks reference nothing off it. */
public final class StartBlockHandler implements BlockHandler {
  @Override
  public BlockOutput execute(RunContext ctx, DagNode node) {
    return BlockOutput.of(Map.of());
  }
}
