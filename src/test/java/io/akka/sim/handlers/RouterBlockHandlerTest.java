package io.akka.sim.handlers;

import static org.junit.jupiter.api.Assertions.*;

import io.akka.sim.domain.BlockConfig;
import io.akka.sim.domain.BlockType;
import io.akka.sim.domain.Dag;
import io.akka.sim.domain.DagEdge;
import io.akka.sim.domain.WorkflowDefinition;
import java.util.List;
import org.junit.jupiter.api.Test;

/** SPEC-001 §4 open decision — question-log row 12. */
class RouterBlockHandlerTest {

  private static Dag dagWith(String selectorRef) {
    var def =
        new WorkflowDefinition(
            "t",
            List.of(
                new WorkflowDefinition.BlockSpec(
                    "router", "Router", BlockType.ROUTER, new BlockConfig.Router(selectorRef)),
                new WorkflowDefinition.BlockSpec("a", "A", BlockType.AGENT, new BlockConfig.Agent(""))),
            List.of(DagEdge.handled("router", "a", "router-branchA")));
    return Dag.build(def);
  }

  @Test
  void selectsRouteMatchingLiteral() {
    var dag = dagWith("branchA");
    var ctx = new RunContext(dag, new EchoAgentCaller());

    var output = new RouterBlockHandler().execute(ctx, dag.nodes.get("router"));

    assertEquals("branchA", output.selectedRoute());
    assertFalse(output.hasError());
  }

  @Test
  void unmatchedRouteIsARunError() {
    var dag = dagWith("nonexistent");
    var ctx = new RunContext(dag, new EchoAgentCaller());

    var output = new RouterBlockHandler().execute(ctx, dag.nodes.get("router"));

    assertTrue(output.hasError());
  }
}
