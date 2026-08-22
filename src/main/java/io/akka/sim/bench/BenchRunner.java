package io.akka.sim.bench;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.akka.sim.domain.BlockConfig;
import io.akka.sim.domain.BlockOutput;
import io.akka.sim.domain.BlockType;
import io.akka.sim.domain.Dag;
import io.akka.sim.domain.DagEdge;
import io.akka.sim.domain.EdgeManager;
import io.akka.sim.domain.WorkflowDefinition;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.TreeSet;

/**
 * The port's side of the benchmark: this rebuild's {@link EdgeManager} answering every workload
 * in {@code sim-port/bench/workloads.json}, in the same shape
 * {@code sim-src/apps/sim/executor/execution/__bench_probe.test.ts} printed for the source.
 *
 * <pre>
 *   java -cp target/classes:&lt;jackson&gt; io.akka.sim.bench.BenchRunner workloads.json answers
 *   java -cp ... io.akka.sim.bench.BenchRunner workloads.json timings
 * </pre>
 */
public final class BenchRunner {

  private static final ObjectMapper JSON = new ObjectMapper();

  private BenchRunner() {}

  public static void main(String[] args) throws IOException {
    var workloads = (ArrayNode) JSON.readTree(Files.readString(Path.of(args[0])));
    var mode = args.length > 1 ? args[1] : "answers";
    if (mode.equals("timings")) {
      System.out.println(JSON.writerWithDefaultPrettyPrinter().writeValueAsString(timings(workloads)));
    } else {
      System.out.println(JSON.writerWithDefaultPrettyPrinter().writeValueAsString(answers(workloads)));
    }
  }

  private static ObjectNode answers(ArrayNode workloads) {
    var out = JSON.createObjectNode();
    for (var w : workloads) {
      out.set(w.get("name").asText(), runWorkload(w));
    }
    return out;
  }

  private static ObjectNode timings(ArrayNode workloads) {
    var out = JSON.createObjectNode();
    for (var w : workloads) {
      var start = System.nanoTime();
      for (int i = 0; i < 200; i++) {
        runWorkload(w);
      }
      var elapsedMs = (System.nanoTime() - start) / 1_000_000.0;
      out.put(w.get("name").asText(), elapsedMs / 200.0);
    }
    return out;
  }

  private static ObjectNode runWorkload(JsonNode w) {
    var dag = buildDag(w);
    var edgeManager = new EdgeManager(dag);

    var readyPerFire = JSON.createArrayNode();
    for (var fire : w.get("fire_sequence")) {
      var node = dag.nodes.get(fire.get("node").asText());
      var output = toBlockOutput(fire.get("output"));
      var ready = edgeManager.processOutgoingEdges(node, output);
      var readyArray = JSON.createArrayNode();
      ready.forEach(readyArray::add);
      readyPerFire.add(readyArray);
    }

    var finalReady = new TreeSet<String>();
    for (var idNode : w.get("nodes")) {
      var id = idNode.asText();
      if (edgeManager.isNodeReady(dag.nodes.get(id))) {
        finalReady.add(id);
      }
    }
    var finalReadyArray = JSON.createArrayNode();
    finalReady.forEach(finalReadyArray::add);

    var result = JSON.createObjectNode();
    result.set("readyPerFire", readyPerFire);
    result.set("finalReady", finalReadyArray);
    return result;
  }

  private static Dag buildDag(JsonNode w) {
    var blocks = new ArrayList<WorkflowDefinition.BlockSpec>();
    for (var idNode : w.get("nodes")) {
      var id = idNode.asText();
      blocks.add(new WorkflowDefinition.BlockSpec(id, id, BlockType.AGENT, new BlockConfig.Agent("")));
    }
    var edges = new ArrayList<DagEdge>();
    for (var e : w.get("edges")) {
      var handle = e.get("handle").isNull() ? null : e.get("handle").asText();
      edges.add(new DagEdge(e.get("source").asText(), e.get("target").asText(), handle));
    }
    return Dag.build(new WorkflowDefinition(w.get("name").asText(), blocks, edges));
  }

  private static BlockOutput toBlockOutput(JsonNode outputNode) {
    String selectedOption = outputNode.has("selectedOption") ? outputNode.get("selectedOption").asText() : null;
    String selectedRoute = outputNode.has("selectedRoute") ? outputNode.get("selectedRoute").asText() : null;
    return new BlockOutput(java.util.Map.of(), null, selectedOption, selectedRoute);
  }
}
