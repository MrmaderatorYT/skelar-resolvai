package com.skelar.defuse.service.llm;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.skelar.defuse.model.domain.Ticket;
import com.skelar.defuse.model.dto.TicketAnalysis;
import com.skelar.defuse.store.InMemoryStore;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/** Recorded analysis replay is read-only: it never re-executes billing or calls a model. */
@Service
public class ReplayService {
    private final LlmSettings settings; private final ObjectMapper mapper; private final InMemoryStore store;
    private final Path file;
    public ReplayService(LlmSettings settings,ObjectMapper mapper,InMemoryStore store,
                         @Value("${defuse.llm.replay-file:replay/analyses.json}") String file) {
        this.settings=settings; this.mapper=mapper; this.store=store; this.file=Path.of(file);
    }
    public synchronized Map<String,Object> record() {
        if (!"live".equals(settings.mode())) throw new IllegalStateException("Only a live Gemma run can be recorded");
        synchronized(store) {
            Map<String,Object> analyses=new LinkedHashMap<>();
            for (var a:store.analyses.values()) {
                if (!"live".equals(a.llmMode)) continue;
                Ticket t=store.tickets.get(a.ticketId);
                analyses.put(a.ticketId,Map.of("ticket_sha256",hash(t.text),"customer_id",t.customerId,"analysis",a));
            }
            if (analyses.isEmpty()) throw new IllegalStateException("No live analyses to record");
            Map<String,Object> out=new LinkedHashMap<>();
            out.put("schema_version",1); out.put("provider",settings.provider()); out.put("model",settings.model());
            out.put("recorded_at",Instant.now().toString()); out.put("analyses",analyses);
            try {
                Files.createDirectories(file.toAbsolutePath().getParent());
                Path tmp=Files.createTempFile(file.toAbsolutePath().getParent(),"replay-",".tmp");
                try {
                    mapper.writerWithDefaultPrettyPrinter().writeValue(tmp.toFile(),out);
                    try { Files.move(tmp,file.toAbsolutePath(),StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE); }
                    catch(AtomicMoveNotSupportedException e) { Files.move(tmp,file.toAbsolutePath(),StandardCopyOption.REPLACE_EXISTING); }
                } finally { Files.deleteIfExists(tmp); }
                return Map.of("provider",settings.provider(),"model",settings.model(),"recorded_at",out.get("recorded_at"),"analyses",analyses.size());
            } catch(java.io.IOException e) { throw new IllegalStateException("Cannot record replay",e); }
        }
    }
    public TicketAnalysis restore(Ticket ticket) {
        if (!"replay".equals(settings.mode())) throw new IllegalStateException("Replay mode is not enabled");
        try {
            if (Files.size(file)>16_000_000) throw new IllegalStateException("Replay file is too large");
            JsonNode root=mapper.readTree(file.toFile()), entry=root.path("analyses").path(ticket.id);
            if (root.path("schema_version").asInt()!=1 || !"gemma".equals(root.path("provider").asText())
                    || entry.isMissingNode() || !hash(ticket.text).equals(entry.path("ticket_sha256").asText())
                    || !ticket.customerId.equals(entry.path("customer_id").asText()))
                throw new IllegalStateException("No matching recorded live analysis; replay never falls back to gold/live");
            TicketAnalysis a=mapper.treeToValue(entry.path("analysis"),TicketAnalysis.class);
            if (!ticket.id.equals(a.ticketId) || !"live".equals(a.llmMode)) throw new IllegalStateException("Invalid replay provenance");
            a.llmMode="replay"; a.llmProvider=root.path("provider").asText(); a.llmModel=root.path("model").asText();
            a.recordedAt=root.path("recorded_at").asText();
            store.analyses.put(ticket.id,a); ticket.status=a.status;
            return a;
        } catch(java.io.IOException e) { throw new IllegalStateException("Replay cache unavailable; record a live run first",e); }
    }
    private static String hash(String text) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8))); }
        catch(java.security.NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
}
