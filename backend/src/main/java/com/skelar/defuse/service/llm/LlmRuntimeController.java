package com.skelar.defuse.service.llm;
import java.util.Map;
import org.springframework.web.bind.annotation.*;
@RestController
@RequestMapping("/api/demo")
public class LlmRuntimeController {
    private final ReplayService replay; private final LlmSettings settings;
    public LlmRuntimeController(ReplayService replay,LlmSettings settings) { this.replay=replay; this.settings=settings; }
    @PostMapping("/record") public Map<String,Object> record() { return replay.record(); }
    @GetMapping("/llm") public Map<String,Object> info() {
        return Map.of("provider",settings.provider(),"model",settings.model(),"mode",settings.mode(),"replay_read_only",true);
    }
}
