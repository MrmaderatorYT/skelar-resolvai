package com.skelar.defuse.controller;

import com.skelar.defuse.auth.SessionService;
import com.skelar.defuse.model.dto.Requests;
import com.skelar.defuse.model.dto.TicketAnalysis;
import com.skelar.defuse.service.TicketPipelineService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Human-in-the-loop: Approve runs the exact actions the reviewer saw (bound by action_hash). */
@RestController
@RequestMapping("/api/tickets/{id}")
public class ActionController {
    private final TicketPipelineService pipeline;

    public ActionController(TicketPipelineService pipeline) {
        this.pipeline = pipeline;
    }

    /** Audit actor comes from the authenticated session (AuthFilter), never from the request body. */
    static String actor(HttpServletRequest request) {
        var session = (SessionService.Session) request.getAttribute("defuse.session");
        if (session == null) return "auth-disabled";
        return session.customerId() == null ? session.role() : session.role() + ":" + session.customerId();
    }

    @PostMapping({"/approve", "/execute"})
    public TicketAnalysis approve(@PathVariable String id, @Valid @RequestBody Requests.Approve body, HttpServletRequest request) {
        return pipeline.approve(id, actor(request), body.replyText(), body.analysisId(), body.actionHash());
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
    public TicketAnalysis humanReply(@PathVariable String id, @Valid @RequestBody Requests.HumanReply body, HttpServletRequest request) {
        return pipeline.humanReply(id, body.text(), actor(request));
    }

    @PostMapping("/reject")
    public TicketAnalysis reject(@PathVariable String id, @Valid @RequestBody Requests.Reject body, HttpServletRequest request) {
        return pipeline.reject(id, actor(request), body.reason().trim());
    }
}
