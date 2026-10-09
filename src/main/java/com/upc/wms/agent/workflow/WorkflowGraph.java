package com.upc.wms.agent.workflow;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class WorkflowGraph {

    private final String id;
    private final Map<String, WorkflowNode> nodes;
    private final List<WorkflowEdge> edges;

    public WorkflowGraph(String id, Collection<WorkflowNode> nodes, Collection<WorkflowEdge> edges) {
        this.id = id;
        Map<String, WorkflowNode> nodeMap = new LinkedHashMap<>();
        for (WorkflowNode node : nodes) {
            if (nodeMap.putIfAbsent(node.id(), node) != null) {
                throw new IllegalArgumentException("Duplicate workflow node: " + node.id());
            }
        }
        this.nodes = Collections.unmodifiableMap(nodeMap);
        this.edges = List.copyOf(edges);
        validate();
    }

    public String id() {
        return id;
    }

    public Collection<WorkflowNode> nodes() {
        return nodes.values();
    }

    public WorkflowNode node(String nodeId) {
        return nodes.get(nodeId);
    }

    public List<WorkflowEdge> edges() {
        return edges;
    }

    public List<WorkflowEdge> incoming(String nodeId) {
        return edges.stream().filter(edge -> edge.to().equals(nodeId)).toList();
    }

    public Set<String> predecessors(String nodeId) {
        Set<String> result = new LinkedHashSet<>();
        incoming(nodeId).forEach(edge -> result.add(edge.from()));
        return result;
    }

    public static WorkflowGraph sequence(String id, List<String> agentNames) {
        List<WorkflowNode> nodes = new ArrayList<>();
        List<WorkflowEdge> edges = new ArrayList<>();
        for (int i = 0; i < agentNames.size(); i++) {
            String nodeId = "step-" + (i + 1) + "-" + agentNames.get(i);
            nodes.add(WorkflowNode.of(nodeId, agentNames.get(i)));
            if (i > 0) {
                edges.add(WorkflowEdge.always(nodes.get(i - 1).id(), nodeId));
            }
        }
        return new WorkflowGraph(id, nodes, edges);
    }

    private void validate() {
        for (WorkflowEdge edge : edges) {
            if (!nodes.containsKey(edge.from()) || !nodes.containsKey(edge.to())) {
                throw new IllegalArgumentException("Workflow edge references an unknown node: " + edge);
            }
        }
        Map<String, Integer> indegree = new LinkedHashMap<>();
        nodes.keySet().forEach(node -> indegree.put(node, 0));
        edges.forEach(edge -> indegree.compute(edge.to(), (ignored, value) -> value + 1));
        ArrayDeque<String> ready = new ArrayDeque<>();
        indegree.forEach((node, degree) -> {
            if (degree == 0) ready.add(node);
        });
        int visited = 0;
        while (!ready.isEmpty()) {
            String current = ready.remove();
            visited++;
            for (WorkflowEdge edge : edges) {
                if (edge.from().equals(current)) {
                    int degree = indegree.compute(edge.to(), (ignored, value) -> value - 1);
                    if (degree == 0) ready.add(edge.to());
                }
            }
        }
        if (visited != nodes.size()) {
            throw new IllegalArgumentException("Workflow graph must be acyclic");
        }
    }
}
