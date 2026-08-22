package io.akka.sim.handlers;

import io.akka.sim.domain.BlockOutput;
import io.akka.sim.domain.DagNode;

/** Executes one block given the run's shared context — SPEC-001 §2. */
public interface BlockHandler {
  BlockOutput execute(RunContext ctx, DagNode node);
}
