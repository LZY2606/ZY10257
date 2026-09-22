package dev.lasso.model;

import java.util.Map;

public record ModelState(String id, String label, Map<String, Object> values) {
}
