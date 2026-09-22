package dev.lasso.model;

public record Transition(String id, String source, String target, String guard, String description) {
}
