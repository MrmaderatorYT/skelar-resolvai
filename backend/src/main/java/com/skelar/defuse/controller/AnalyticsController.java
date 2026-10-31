package com.skelar.defuse.controller;

import com.skelar.defuse.service.analytics.AnalyticsService;
import com.skelar.defuse.service.analytics.EvalService;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/analytics")
public class AnalyticsController {
    private final AnalyticsService analytics;
    private final EvalService eval;

    public AnalyticsController(AnalyticsService analytics, EvalService eval) {
        this.analytics = analytics;
        this.eval = eval;
    }

    @GetMapping("/summary")
    public Map<String, Object> summary() {
        return analytics.summary();
    }

    @GetMapping("/root-causes")
    public List<Map<String, Object>> rootCauses() {
        return analytics.rootCauses();
    }

    @GetMapping("/dashboard")
    public Map<String, Object> dashboard() {
        Map<String, Object> out = new LinkedHashMap<>(analytics.summary());
        out.put("root_causes", analytics.rootCauses());
        return out;
    }

    @GetMapping("/diagnostics") public Map<String, Object> diagnostics() { return analytics.diagnostics(); }

    @GetMapping("/eval")
    public Map<String, Object> evalReport() {
        return eval.report();
    }

    /** Runs the live classifier over the 38 eval tickets (~38 Claude Haiku calls). */
    @PostMapping("/eval/run")
    public EvalService.Result runEval() {
        return eval.runLive();
    }
}
