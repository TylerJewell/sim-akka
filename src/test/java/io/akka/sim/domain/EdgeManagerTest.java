package io.akka.sim.domain;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** SPEC-001 §3 rules 1, 2, 5 — question-log rows 5, 6. */
class EdgeManagerTest {

  private static Dag linearStartCondition() {
    var def =
        new WorkflowDefinition(
            "t",
            List.of(
                new WorkflowDefinition.BlockSpec("start", "Start", BlockType.START, new BlockConfig.Start()),
                new WorkflowDefinition.BlockSpec(
                    "cond", "Cond", BlockType.CONDITION, new BlockConfig.Condition(List.of()))),
            List.of(DagEdge.of("start", "cond")));
    return Dag.build(def);
  }

  @Test
  void nodeWithNoIncomingEdgesIsReady() {
    var dag = linearStartCondition();
    var edgeManager = new EdgeManager(dag);
    assertTrue(edgeManager.isNodeReady(dag.nodes.get("start")));
  }

  @Test
  void nodeWithUnresolvedIncomingEdgeIsNotReady() {
    var dag = linearStartCondition();
    var edgeManager = new EdgeManager(dag);
    assertFalse(edgeManager.isNodeReady(dag.nodes.get("cond")));
  }

  @Test
  void activatingTheOnlyIncomingEdgeMakesTargetReady() {
    var dag = linearStartCondition();
    var edgeManager = new EdgeManager(dag);
    var ready = edgeManager.processOutgoingEdges(dag.nodes.get("start"), BlockOutput.of(Map.of()));
    assertEquals(List.of("cond"), ready);
    assertTrue(edgeManager.isNodeReady(dag.nodes.get("cond")));
  }

  /**
   * A condition selects the "true" branch; the "false" branch is a dead end that cascades
   * forward and unblocks the convergence node once the "true" branch also arrives there.
   * Rule 2 (question-log row 6).
   */
  @Test
  void deadBranchCascadesAndConvergenceWaitsForTheLiveBranch() {
    var def =
        new WorkflowDefinition(
            "t",
            List.of(
                block("start", BlockType.START),
                block("cond", BlockType.CONDITION),
                block("onTrue", BlockType.AGENT),
                block("onFalse", BlockType.AGENT),
                block("merge", BlockType.RESPONSE)),
            List.of(
                DagEdge.of("start", "cond"),
                DagEdge.handled("cond", "onTrue", "condition-true"),
                DagEdge.handled("cond", "onFalse", "condition-false"),
                DagEdge.of("onTrue", "merge"),
                DagEdge.of("onFalse", "merge")));
    var dag = Dag.build(def);
    var edgeManager = new EdgeManager(dag);

    // Condition selects "true": onTrue activates, onFalse's edge deactivates.
    var afterCondition =
        edgeManager.processOutgoingEdges(dag.nodes.get("cond"), BlockOutput.conditionMatch(Map.of(), "true"));
    assertEquals(List.of("onTrue"), afterCondition);
    // merge is not ready yet: onFalse never ran, but its dead edge into merge already
    // cascaded-deactivated, so merge only still waits on onTrue.
    assertFalse(edgeManager.isNodeReady(dag.nodes.get("merge")));

    // onTrue completes; its edge into merge activates, and merge's last pending edge (from
    // onFalse) was already resolved by the earlier cascade — merge is now ready.
    var afterOnTrue =
        edgeManager.processOutgoingEdges(dag.nodes.get("onTrue"), BlockOutput.of(Map.of()));
    assertEquals(List.of("merge"), afterOnTrue);
  }

  @Test
  void conditionRoutesByExactHandleMatchNotIndex() {
    var def =
        new WorkflowDefinition(
            "t",
            List.of(block("cond", BlockType.CONDITION), block("a", BlockType.AGENT), block("b", BlockType.AGENT)),
            List.of(
                DagEdge.handled("cond", "a", "condition-alpha"),
                DagEdge.handled("cond", "b", "condition-beta")));
    var dag = Dag.build(def);
    var edgeManager = new EdgeManager(dag);

    var ready =
        edgeManager.processOutgoingEdges(dag.nodes.get("cond"), BlockOutput.conditionMatch(Map.of(), "beta"));
    assertEquals(List.of("b"), ready);
  }

  /**
   * A node that already received a real (activated) incoming edge from one source must not be
   * cascaded into as a dead end when a *different* source's edge into it deactivates afterward,
   * even if that deactivation is what makes it ready. The guard lives in
   * {@code EdgeManager.deactivateAndCascade}'s {@code nodesWithActivatedEdge} check — without it,
   * a node that both (a) already has real input and (b) becomes ready only via a later,
   * independent deactivation is never reported ready at all.
   */
  @Test
  void cascadeDoesNotSkipReportingANodeThatAlreadyReceivedRealInput() {
    var def =
        new WorkflowDefinition(
            "t",
            List.of(
                block("start", BlockType.START),
                block("cond1", BlockType.CONDITION),
                block("cond2", BlockType.CONDITION),
                block("deadA", BlockType.AGENT),
                block("other", BlockType.AGENT),
                block("merge", BlockType.RESPONSE)),
            List.of(
                DagEdge.of("start", "cond1"),
                DagEdge.of("start", "cond2"),
                DagEdge.handled("cond1", "merge", "condition-true"),
                DagEdge.handled("cond1", "deadA", "condition-false"),
                DagEdge.handled("cond2", "other", "condition-true"),
                DagEdge.handled("cond2", "merge", "condition-false")));
    var dag = Dag.build(def);
    var edgeManager = new EdgeManager(dag);

    // cond1 selects "true": activates its edge straight into merge. merge still has cond2's
    // edge pending, so it is not ready yet.
    var afterCond1 =
        edgeManager.processOutgoingEdges(dag.nodes.get("cond1"), BlockOutput.conditionMatch(Map.of(), "true"));
    assertEquals(List.of(), afterCond1);
    assertFalse(edgeManager.isNodeReady(dag.nodes.get("merge")));

    // cond2 selects "true" too, which deactivates ITS edge into merge — merge's last pending
    // edge resolves via deactivation, not activation, and must still be reported ready.
    var afterCond2 =
        edgeManager.processOutgoingEdges(dag.nodes.get("cond2"), BlockOutput.conditionMatch(Map.of(), "true"));
    assertEquals(List.of("other", "merge"), afterCond2);
  }

  /**
   * The guard also stops the cascade from continuing PAST a node with real input into that
   * node's own outgoing edges. Without it, a dead branch reaching a live node "merge" would keep
   * cascading into "merge"'s own edge to "after", wrongly deactivating it before "merge" ever
   * runs — which would let a second, unrelated source into "after" report it ready the moment
   * *that* source resolves, without ever waiting for "merge" to actually execute.
   */
  @Test
  void cascadeStopsAtALiveNodeInsteadOfDeactivatingItsOwnOutgoingEdges() {
    var def =
        new WorkflowDefinition(
            "t",
            List.of(
                block("start", BlockType.START),
                block("cond1", BlockType.CONDITION),
                block("cond2", BlockType.CONDITION),
                block("deadA", BlockType.AGENT),
                block("other", BlockType.AGENT),
                block("merge", BlockType.AGENT),
                block("y", BlockType.AGENT),
                block("after", BlockType.RESPONSE)),
            List.of(
                DagEdge.of("start", "cond1"),
                DagEdge.of("start", "cond2"),
                DagEdge.of("start", "y"),
                DagEdge.handled("cond1", "merge", "condition-true"),
                DagEdge.handled("cond1", "deadA", "condition-false"),
                DagEdge.handled("cond2", "other", "condition-true"),
                DagEdge.handled("cond2", "merge", "condition-false"),
                DagEdge.of("merge", "after"),
                DagEdge.of("y", "after")));
    var dag = Dag.build(def);
    var edgeManager = new EdgeManager(dag);

    // cond1 gives merge real input; cond2's dead edge into merge then resolves merge (as in the
    // test above) but must not cascade on into merge -> after.
    edgeManager.processOutgoingEdges(dag.nodes.get("cond1"), BlockOutput.conditionMatch(Map.of(), "true"));
    edgeManager.processOutgoingEdges(dag.nodes.get("cond2"), BlockOutput.conditionMatch(Map.of(), "true"));

    // y is a second, independent source into "after". Firing it must NOT make "after" ready —
    // "after" still has to wait for "merge" to actually run and activate its own edge.
    var afterY = edgeManager.processOutgoingEdges(dag.nodes.get("y"), BlockOutput.of(Map.of()));
    assertEquals(List.of(), afterY);
    assertFalse(edgeManager.isNodeReady(dag.nodes.get("after")));
  }

  private static WorkflowDefinition.BlockSpec block(String id, BlockType type) {
    BlockConfig config =
        switch (type) {
          case START -> new BlockConfig.Start();
          case AGENT -> new BlockConfig.Agent("");
          case CONDITION -> new BlockConfig.Condition(List.of());
          case ROUTER -> new BlockConfig.Router("");
          case RESPONSE -> new BlockConfig.Response();
        };
    return new WorkflowDefinition.BlockSpec(id, id, type, config);
  }
}
