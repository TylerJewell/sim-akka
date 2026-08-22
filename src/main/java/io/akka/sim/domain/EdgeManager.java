package io.akka.sim.domain;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Edge activation and readiness for one run — SPEC-001 §3 rules 1, 2 and 5. Ported from
 * {@code executor/execution/edge-manager.ts}'s non-loop, non-parallel path (question-log row 11);
 * the sentinel/subflow cascade machinery that file also carries is out of scope (SPEC-001 §1).
 */
public final class EdgeManager {

  private static final String ERROR_HANDLE = "error";
  private static final String CONDITION_PREFIX = "condition-";
  private static final String ROUTER_PREFIX = "router-";

  private final Dag dag;
  private final Set<String> deactivatedEdges = new HashSet<>();
  private final Set<String> nodesWithActivatedEdge = new HashSet<>();

  public EdgeManager(Dag dag) {
    this.dag = dag;
  }

  /** Rule 1: ready when there are no incoming edges left unresolved. */
  public boolean isNodeReady(DagNode node) {
    if (node.incomingEdges.isEmpty()) return true;
    for (String sourceId : node.incomingEdges) {
      if (!deactivatedEdges.contains(edgeKey(sourceId, node.id))) {
        return false;
      }
    }
    return true;
  }

  /**
   * Activates or deactivates every outgoing edge of {@code node} given its output, cascading
   * deactivation through dead branches (rule 2), and returns the ids of nodes that became ready
   * as a result — either because {@code node} activated an edge into them, or because a cascading
   * deactivation resolved their last pending incoming edge.
   */
  public List<String> processOutgoingEdges(DagNode node, BlockOutput output) {
    var readyNodes = new LinkedHashSet<String>();
    var activatedTargets = new ArrayList<String>();
    var toDeactivate = new ArrayList<DagEdge>();

    for (DagEdge edge : node.outgoingEdges) {
      if (shouldActivate(edge, output)) {
        activatedTargets.add(edge.target());
      } else {
        toDeactivate.add(edge);
      }
    }

    // Marked before cascading, matching the source: a target this same node both activates and
    // (via a different edge) deactivates must already read as "has real input" to the cascade.
    for (String targetId : activatedTargets) {
      nodesWithActivatedEdge.add(targetId);
    }

    for (var edge : toDeactivate) {
      deactivateAndCascade(node.id, edge.target());
    }

    for (String targetId : activatedTargets) {
      var targetNode = dag.nodes.get(targetId);
      if (targetNode != null) {
        targetNode.incomingEdges.remove(node.id);
      }
    }

    for (String targetId : activatedTargets) {
      var targetNode = dag.nodes.get(targetId);
      if (targetNode != null && isNodeReady(targetNode)) {
        readyNodes.add(targetId);
      }
    }

    // Only THIS node's own direct dead edges are checked for having become ready by
    // deactivation — a target several hops down a cascade is reported ready later, by
    // whichever direct edge into it triggers the check (its own source's activation or
    // its own source's deactivation), not by the cascade that passed through it here.
    for (var edge : toDeactivate) {
      var targetId = edge.target();
      var targetNode = dag.nodes.get(targetId);
      if (targetNode == null) continue;
      if (readyNodes.contains(targetId) || activatedTargets.contains(targetId)) continue;
      if (nodesWithActivatedEdge.contains(targetId) && isNodeReady(targetNode)) {
        readyNodes.add(targetId);
      }
    }

    return List.copyOf(readyNodes);
  }

  /**
   * Marks {@code sourceId -> targetId} deactivated and, if the target is now fully resolved with
   * no real activated input of its own, cascades into its outgoing edges too — a genuine dead end
   * never runs, so its own downstream edges are dead as well. A target that already holds (or
   * later gains) real activated input is never cascaded past; whether it becomes ready is decided
   * by {@link #processOutgoingEdges}'s direct-edge check, not by this recursion.
   */
  private void deactivateAndCascade(String sourceId, String targetId) {
    var key = edgeKey(sourceId, targetId);
    if (deactivatedEdges.contains(key)) return;
    deactivatedEdges.add(key);

    var targetNode = dag.nodes.get(targetId);
    if (targetNode == null) return;
    if (!isNodeReady(targetNode)) return;
    if (nodesWithActivatedEdge.contains(targetId)) return;

    for (DagEdge outgoing : targetNode.outgoingEdges) {
      deactivateAndCascade(targetId, outgoing.target());
    }
  }

  private boolean shouldActivate(DagEdge edge, BlockOutput output) {
    var handle = edge.sourceHandle();
    if (handle == null) {
      return !output.hasError();
    }
    if (handle.equals(ERROR_HANDLE)) {
      return output.hasError();
    }
    if (output.hasError()) {
      return false;
    }
    if (handle.startsWith(CONDITION_PREFIX)) {
      return handle.substring(CONDITION_PREFIX.length()).equals(output.selectedOption());
    }
    if (handle.startsWith(ROUTER_PREFIX)) {
      return handle.substring(ROUTER_PREFIX.length()).equals(output.selectedRoute());
    }
    return true;
  }

  private static String edgeKey(String source, String target) {
    return source + "->" + target;
  }
}
