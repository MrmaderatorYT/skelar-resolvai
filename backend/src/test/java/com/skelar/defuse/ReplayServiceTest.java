package com.skelar.defuse;
import static org.assertj.core.api.Assertions.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.skelar.defuse.service.TicketPipelineService;
import com.skelar.defuse.service.llm.*;
import com.skelar.defuse.store.InMemoryStore;
import java.nio.file.Path;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
@SpringBootTest
class ReplayServiceTest {
    @Autowired InMemoryStore store; @Autowired TicketPipelineService pipeline; @Autowired ObjectMapper mapper;
    @TempDir Path tmp;
    private LlmSettings settings(String mode) { return new LlmSettings(mode,"gemma","gemma-4-31b-it","GEMINI_API_KEY",15000,2,false,false); }
    @Test void recordedReplayIsBoundToTicketAndDoesNotRunBilling() {
        store.reset();var original=pipeline.analyze("T01",false);original.llmMode="live"; // synthetic unit fixture, not live evidence
        Path file=tmp.resolve("analyses.json");new ReplayService(settings("live"),mapper,store,file.toString()).record();
        store.reset();var cached=new ReplayService(settings("replay"),mapper,store,file.toString()).restore(store.tickets.get("T01"));
        assertThat(cached.llmMode).isEqualTo("replay");assertThat(cached.recordedAt).isNotBlank();
        assertThat(store.payment(cached.context.lastPayment().id()).status).isEqualTo("succeeded");
        assertThatThrownBy(()->pipeline.approve("T01","test",null,cached.analysisId)).isInstanceOf(IllegalStateException.class);
        store.tickets.get("T01").text="different ticket";
        assertThatThrownBy(()->new ReplayService(settings("replay"),mapper,store,file.toString()).restore(store.tickets.get("T01"))).isInstanceOf(IllegalStateException.class);
    }
    @Test void goldCannotBeRecordedAsALiveRun() {
        store.reset();pipeline.analyze("T01",false);
        assertThatThrownBy(()->new ReplayService(settings("gold"),mapper,store,tmp.resolve("none.json").toString()).record())
                .isInstanceOf(IllegalStateException.class);
    }
}
