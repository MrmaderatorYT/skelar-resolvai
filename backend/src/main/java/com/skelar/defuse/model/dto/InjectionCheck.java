package com.skelar.defuse.model.dto;

import java.util.List;

public record InjectionCheck(boolean flagged, List<String> patterns) {
}
