package io.akka.sim.domain;

import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Resolves {@code <name.path>} references inside a block's textual input against prior blocks'
 * outputs — SPEC-001 §3 rule 6. Ported from {@code executor/variables/resolver.ts} and
 * {@code normalizeWorkflowBlockName} (question-log row 4).
 */
public final class ReferenceResolver {

  private static final Pattern REFERENCE = Pattern.compile("<([^<>]+)>");

  private ReferenceResolver() {}

  /** Lowercased, whitespace stripped, {@code .} stripped — question-log row 4. */
  public static String normalizeName(String name) {
    return name.toLowerCase(Locale.ROOT).replaceAll("\\s+", "").replace(".", "");
  }

  /**
   * Replaces every {@code <name.path>} in {@code text} with the value found by normalizing
   * {@code name} against {@code outputsByNormalizedName} and walking the dot-delimited
   * {@code path} into that block's fields. A reference that cannot be resolved (unknown name, or
   * missing path) is left in the text unchanged, matching the source's best-effort resolution.
   */
  public static String resolve(String text, Map<String, BlockOutput> outputsByNormalizedName) {
    if (text == null) return null;
    Matcher matcher = REFERENCE.matcher(text);
    StringBuilder result = new StringBuilder();
    int lastEnd = 0;
    while (matcher.find()) {
      String reference = matcher.group(1);
      Object resolved = resolveOne(reference, outputsByNormalizedName);
      result.append(text, lastEnd, matcher.start());
      result.append(resolved == null ? matcher.group(0) : String.valueOf(resolved));
      lastEnd = matcher.end();
    }
    result.append(text.substring(lastEnd));
    return result.toString();
  }

  private static Object resolveOne(String reference, Map<String, BlockOutput> outputsByNormalizedName) {
    int dot = reference.indexOf('.');
    if (dot < 0) return null;
    String namePart = normalizeName(reference.substring(0, dot));
    String path = reference.substring(dot + 1);

    var output = outputsByNormalizedName.get(namePart);
    if (output == null) return null;

    Object current = output.fields();
    for (String segment : path.split("\\.")) {
      if (!(current instanceof Map<?, ?> map) || !map.containsKey(segment)) {
        return null;
      }
      current = map.get(segment);
    }
    return current;
  }
}
