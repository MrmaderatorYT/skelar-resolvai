package com.skelar.defuse.controller;

import com.skelar.defuse.model.dto.Requests;
import com.skelar.defuse.model.dto.TicketAnalysis;
import com.skelar.defuse.service.TicketPipelineService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Human-in-the-loop: Approve runs the actions the policy left in "suggest" mode. */
@RestController
@RequestMapping("/api/tickets/{id}")
public class ActionController {
    private final TicketPipelineService pipeline;

    public ActionController(TicketPipelineService pipeline) {
        this.pipeline = pipeline;
    }

    @PostMapping({"/approve", "/execute"})
    public TicketAnalysis approve(@PathVariable String id, @Valid @RequestBody Requests.Approve body) {
        return pipeline.approve(id, "support-agent", body.replyText(), body.analysisId());
    }

    @PostMapping("/offer/accept")
    public TicketAnalysis acceptOffer(@PathVariable String id) {
        return pipeline.acceptOffer(id);
    }

    @PostMapping("/offer/decline")
    public TicketAnalysis declineOffer(@PathVariable String id) {
        return pipeline.declineOffer(id);
    }

    @PostMapping("/human-reply")
    public TicketAnalysis humanReply(@PathVariable String id, @Valid @RequestBody Requests.HumanReply body) {
        return pipeline.humanReply(id, body.text(), "support-agent");
    }

    @PostMapping("/reject")
    public TicketAnalysis reject(@PathVariable String id, @Valid @RequestBody(required = false) Requests.Reject body) {
        return pipeline.reject(id, "support-agent", body == null ? "" : body.reason());
    }
}
