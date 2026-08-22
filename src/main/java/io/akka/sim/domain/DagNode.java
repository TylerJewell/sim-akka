package io.akka.sim.domain;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * One block in a workflow definition. {@code incomingEdges} holds the ids of source nodes not
 * yet resolved for this run — mutated by {@link EdgeManager} as edges activate or deactivate,
 * mirroring the source's node-scoped edge bookkeeping (SPEC-001 §2, question-log row 6).
 */
public final class DagNode {
  public final String id;
  public final String name;
  public final BlockType blockType;
  public final BlockConfig config;
  public final Set<String> incomingEdges = new LinkedHashSet<>();
  public final List<DagEdge> outgoingEdges = new ArrayList<>();

  public DagNode(String id, String name, BlockType blockType, BlockConfig config) {
    this.id = id;
    this.name = name;
    this.blockType = blockType;
    this.config = config;
  }
}
