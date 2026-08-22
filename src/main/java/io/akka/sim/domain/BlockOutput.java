package io.akka.sim.domain;

import java.util.Map;

/**
 * A block's own result, referenceable by downstream blocks via {@code <name.path>} —
 * SPEC-001 §2. {@code selectedOption} drives condition routing (rule 5); {@code selectedRoute}
 * drives router routing; {@code error} marks the block as failed.
 */
public record BlockOutput(
    Map<String, Object> fields, String error, String selectedOption, String selectedRoute) {

  public static BlockOutput of(Map<String, Object> fields) {
    return new BlockOutput(fields, null, null, null);
  }

  public static BlockOutput failed(String error) {
    return new BlockOutput(Map.of(), error, null, null);
  }

  public static BlockOutput conditionMatch(Map<String, Object> fields, String selectedOption) {
    return new BlockOutput(fields, null, selectedOption, null);
  }

  public static BlockOutput routed(Map<String, Object> fields, String selectedRoute) {
    return new BlockOutput(fields, null, null, selectedRoute);
  }

  public boolean hasError() {
    return error != null;
  }
}
