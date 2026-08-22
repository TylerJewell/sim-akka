package io.akka.sim.handlers;

import io.akka.sim.domain.BlockConfig;
import io.akka.sim.domain.BlockOutput;
import io.akka.sim.domain.DagNode;
import java.util.Map;

/**
 * Evaluates each branch's structured comparison in order and selects the first that matches —
 * SPEC-001 §3 rule 5, §4's simplification of the source's arbitrary-JavaScript expressions.
 */
public final class ConditionBlockHandler implements BlockHandler {
  @Override
  public BlockOutput execute(RunContext ctx, DagNode node) {
    var config = (BlockConfig.Condition) node.config;
    for (var branch : config.branches()) {
      var actual = ctx.resolve(branch.fieldRef());
      if (matches(actual, branch.operator(), branch.literal())) {
        return BlockOutput.conditionMatch(Map.of("matched", branch.id()), branch.id());
      }
    }
    return BlockOutput.failed("no condition branch matched for block " + node.id);
  }

  private static boolean matches(String actual, BlockConfig.Condition.Operator operator, String literal) {
    return switch (operator) {
      case EQUALS -> actual != null && actual.equals(literal);
      case NOT_EQUALS -> actual == null || !actual.equals(literal);
      case CONTAINS -> actual != null && actual.contains(literal);
      case GREATER_THAN -> compareNumeric(actual, literal) > 0;
      case LESS_THAN -> compareNumeric(actual, literal) < 0;
    };
  }

  private static int compareNumeric(String actual, String literal) {
    if (actual == null) return Integer.MIN_VALUE;
    return Double.compare(Double.parseDouble(actual), Double.parseDouble(literal));
  }
}
