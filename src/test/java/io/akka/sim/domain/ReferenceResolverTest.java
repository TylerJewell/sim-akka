package io.akka.sim.domain;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Map;
import org.junit.jupiter.api.Test;

/** SPEC-001 §3 rule 6 — question-log row 4. */
class ReferenceResolverTest {

  @Test
  void normalizesNameByLowercasingStrippingWhitespaceAndDots() {
    assertEquals("triggerdev1", ReferenceResolver.normalizeName("Trigger.dev 1"));
    assertEquals("myagent", ReferenceResolver.normalizeName("  My Agent  "));
    assertEquals("router", ReferenceResolver.normalizeName("Router"));
  }

  @Test
  void resolvesReferenceAgainstNormalizedBlockName() {
    var outputs =
        Map.of("myagent", BlockOutput.of(Map.of("response", "hello")));
    assertEquals("hello", ReferenceResolver.resolve("<My Agent.response>", outputs));
  }

  @Test
  void resolvesNestedDotPath() {
    var outputs =
        Map.of("agent", BlockOutput.of(Map.of("user", Map.of("name", "Ada"))));
    assertEquals("Ada", ReferenceResolver.resolve("<agent.user.name>", outputs));
  }

  @Test
  void leavesUnresolvableReferenceUnchanged() {
    var outputs = Map.<String, BlockOutput>of();
    assertEquals("<unknown.field>", ReferenceResolver.resolve("<unknown.field>", outputs));
  }

  @Test
  void resolvesMultipleReferencesInOneString() {
    var outputs =
        Map.of(
            "a", BlockOutput.of(Map.of("x", "1")),
            "b", BlockOutput.of(Map.of("y", "2")));
    assertEquals("1-2", ReferenceResolver.resolve("<a.x>-<b.y>", outputs));
  }
}
