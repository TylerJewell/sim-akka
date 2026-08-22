package io.akka.sim.handlers;

import static org.junit.jupiter.api.Assertions.*;

import io.akka.sim.domain.BlockConfig;
import io.akka.sim.domain.BlockType;
import io.akka.sim.domain.Dag;
import io.akka.sim.domain.DagEdge;
import io.akka.sim.domain.WorkflowDefinition;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** SPEC-001 §4's structured-comparison simplification of the source's condition block. */
class ConditionBlockHandlerTest {

  private static Dag dagWith(BlockConfig.Condition config) {
    var def =
        new WorkflowDefinition(
            "t",
            List.of(new WorkflowDefinition.BlockSpec("cond", "Cond", BlockType.CONDITION, config)),
            List.of());
    return Dag.build(def);
  }

  @Test
  void firstMatchingBranchWins() {
    var config =
        new BlockConfig.Condition(
            List.of(
                new BlockConfig.Condition.Branch("low", "5", BlockConfig.Condition.Operator.GREATER_THAN, "10"),
                new BlockConfig.Condition.Branch("high", "5", BlockConfig.Condition.Operator.LESS_THAN, "10")));
    var dag = dagWith(config);
    var ctx = new RunContext(dag, new EchoAgentCaller());

    var output = new ConditionBlockHandler().execute(ctx, dag.nodes.get("cond"));

    assertEquals("high", output.selectedOption());
  }

  @Test
  void noMatchingBranchIsAnError() {
    var config =
        new BlockConfig.Condition(
            List.of(new BlockConfig.Condition.Branch("a", "x", BlockConfig.Condition.Operator.EQUALS, "y")));
    var dag = dagWith(config);
    var ctx = new RunContext(dag, new EchoAgentCaller());

    var output = new ConditionBlockHandler().execute(ctx, dag.nodes.get("cond"));

    assertTrue(output.hasError());
  }

  @Test
  void resolvesFieldReferenceBeforeComparing() {
    var config =
        new BlockConfig.Condition(
            List.of(
                new BlockConfig.Condition.Branch(
                    "ok", "<upstream.status>", BlockConfig.Condition.Operator.EQUALS, "ready")));
    var def =
        new WorkflowDefinition(
            "t",
            List.of(
                new WorkflowDefinition.BlockSpec(
                    "upstream", "Upstream", BlockType.AGENT, new BlockConfig.Agent("")),
                new WorkflowDefinition.BlockSpec("cond", "Cond", BlockType.CONDITION, config)),
            List.of(DagEdge.of("upstream", "cond")));
    var dag = Dag.build(def);
    var ctx = new RunContext(dag, new EchoAgentCaller());
    ctx.recordOutput("upstream", io.akka.sim.domain.BlockOutput.of(Map.of("status", "ready")));

    var output = new ConditionBlockHandler().execute(ctx, dag.nodes.get("cond"));

    assertEquals("ok", output.selectedOption());
  }
}
