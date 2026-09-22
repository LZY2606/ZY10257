package dev.lasso.model;

import java.util.List;

public record PropositionSet(String id, String name, String kind, List<String> memberStateIds) {
}
