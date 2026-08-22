package io.akka.sim.domain;

/**
 * A directed edge between two blocks. {@code sourceHandle} is {@code null} for the default (only)
 * outgoing path off a block that never branches — SPEC-001 §2.
 */
public record DagEdge(String source, String target, String sourceHandle) {

  public static DagEdge of(String source, String target) {
    return new DagEdge(source, target, null);
  }

  public static DagEdge handled(String source, String target, String sourceHandle) {
    return new DagEdge(source, target, sourceHandle);
  }
}
