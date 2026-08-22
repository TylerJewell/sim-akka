package io.akka.sim.domain;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A workflow definition turned into a graph ready to run: each node knows its own incoming and
 * outgoing edges. Built fresh per run so {@link EdgeManager}'s in-place mutation of
 * {@link DagNode#incomingEdges} never leaks between runs of the same definition.
 */
public final class Dag {
  public final Map<String, DagNode> nodes = new LinkedHashMap<>();

  public static Dag build(WorkflowDefinition definition) {
    var dag = new Dag();
    for (var block : definition.blocks()) {
      dag.nodes.put(block.id(), new DagNode(block.id(), block.name(), block.blockType(), block.config()));
    }
    for (var edge : definition.edges()) {
      var source = dag.nodes.get(edge.source());
      var target = dag.nodes.get(edge.target());
      if (source == null || target == null) {
        throw new IllegalArgumentException("edge references unknown block: " + edge);
      }
      source.outgoingEdges.add(edge);
      target.incomingEdges.add(edge.source());
    }
    return dag;
  }

  public DagNode startNode() {
    return nodes.values().stream()
        .filter(n -> n.blockType == BlockType.START)
        .findFirst()
        .orElseThrow(() -> new IllegalStateException("no start block in workflow definition"));
  }

  public List<DagNode> all() {
    return List.copyOf(nodes.values());
  }
}
