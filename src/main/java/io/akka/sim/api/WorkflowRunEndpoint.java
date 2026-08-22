package io.akka.sim.api;

import akka.javasdk.annotations.Acl;
import akka.javasdk.annotations.http.Get;
import akka.javasdk.annotations.http.HttpEndpoint;
import akka.javasdk.annotations.http.Post;
import akka.javasdk.client.ComponentClient;
import io.akka.sim.application.WorkflowRunEntity;
import io.akka.sim.domain.WorkflowDefinition;

/** Start a run and monitor it — the two halves of SPEC-001's capability. */
@Acl(allow = @Acl.Matcher(principal = Acl.Principal.ALL))
@HttpEndpoint("/runs")
public class WorkflowRunEndpoint {

  private final ComponentClient componentClient;

  public WorkflowRunEndpoint(ComponentClient componentClient) {
    this.componentClient = componentClient;
  }

  public record StartRunRequest(String runId, WorkflowDefinition definition) {}

  @Post("/{runId}")
  public WorkflowRunView start(String runId, WorkflowDefinition definition) {
    return WorkflowRunView.from(
        runId,
        componentClient
            .forEventSourcedEntity(runId)
            .method(WorkflowRunEntity::startRun)
            .invoke(new WorkflowRunEntity.StartRun(definition)));
  }

  @Get("/{runId}")
  public WorkflowRunView get(String runId) {
    return WorkflowRunView.from(
        runId, componentClient.forEventSourcedEntity(runId).method(WorkflowRunEntity::get).invoke());
  }
}
