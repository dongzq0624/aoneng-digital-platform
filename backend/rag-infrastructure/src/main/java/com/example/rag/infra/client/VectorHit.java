package com.example.rag.infra.client;

import java.util.List;
import java.util.Map;

public record VectorHit(String id, double score, Map<String, Object> payload) {}
