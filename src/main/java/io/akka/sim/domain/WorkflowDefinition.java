package io.akka.sim.domain;

import java.util.List;

/** An immutable workflow: its blocks and the edges between them — SPEC-001 §2. */
public record WorkflowDefinition(String name, List<BlockSpec> blocks, List<DagEdge> edges) {

  public record BlockSpec(String id, String name, BlockType blockType, BlockConfig config) {}
}
