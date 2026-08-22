package io.akka.sim.domain;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import java.util.List;

/** Block-specific configuration — SPEC-001 §2, §4 (condition/agent simplifications). */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
@JsonSubTypes({
  @JsonSubTypes.Type(value = BlockConfig.Start.class, name = "Start"),
  @JsonSubTypes.Type(value = BlockConfig.Agent.class, name = "Agent"),
  @JsonSubTypes.Type(value = BlockConfig.Condition.class, name = "Condition"),
  @JsonSubTypes.Type(value = BlockConfig.Router.class, name = "Router"),
  @JsonSubTypes.Type(value = BlockConfig.Response.class, name = "Response")
})
public sealed interface BlockConfig {

  record Start() implements BlockConfig {}

  /**
   * {@code promptTemplate} may contain {@code <name.path>} references (rule 6), resolved before
   * being handed to the injected {@link io.akka.sim.handlers.AgentCaller}.
   */
  record Agent(String promptTemplate) implements BlockConfig {}

  /**
   * One structured comparison per branch, evaluated in order; the first branch whose comparison
   * is true is the {@code selectedOption} (rule 5) — SPEC-001 §4's deliberate simplification of
   * the source's arbitrary-JavaScript condition expressions.
   */
  record Condition(List<Branch> branches) implements BlockConfig {
    public record Branch(String id, String fieldRef, Operator operator, String literal) {}

    public enum Operator {
      EQUALS,
      NOT_EQUALS,
      CONTAINS,
      GREATER_THAN,
      LESS_THAN
    }
  }

  /**
   * {@code selectorRef} is a {@code <name.path>} reference (rule 6) resolved to a literal value;
   * that value becomes {@code selectedRoute}. Open decision (spec §4): a value matching no
   * outgoing edge's {@code router-<id>} handle is a run error, not a silently dead path.
   */
  record Router(String selectorRef) implements BlockConfig {}

  record Response() implements BlockConfig {}
}
