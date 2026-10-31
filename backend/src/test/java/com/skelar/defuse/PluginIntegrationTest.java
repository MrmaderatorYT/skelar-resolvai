package com.skelar.defuse;
import static org.assertj.core.api.Assertions.*;
import com.skelar.defuse.service.executor.*;
import com.skelar.defuse.service.policy.ContextBuilderService;
import com.skelar.defuse.model.dto.PolicyDecision;
import com.skelar.defuse.registry.*;
import com.skelar.defuse.spi.*;
import com.skelar.defuse.store.InMemoryStore;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
@SpringBootTest
class PluginIntegrationTest {
    @Autowired InMemoryStore store; @Autowired ActionExecutorService executor; @Autowired ContextBuilderService contexts;
    @Autowired com.skelar.defuse.service.audit.AuditService audit;
    @Autowired BillingRegistry billing; @Autowired ConnectorRegistry connectors;
    @BeforeEach void reset() { store.reset(); }
    private ApplicationContextRunner runner() { return new ApplicationContextRunner()
            .withInitializer(new ConfigDataApplicationContextInitializer()).withUserConfiguration(DefuseApplication.class); }
    @Test void unknownConnectorTypeFailsStartup() {
        runner().withPropertyValues("defuse.datasources.customers.type=unregistered").run(c->assertThat(c).hasFailed());
    }
    @Test void literalPasswordFailsStartup() {
        runner().withPropertyValues("defuse.datasources.customers.password=synthetic-test-password").run(c->assertThat(c).hasFailed());
    }
    @Test void literalDsnPasswordRejectedWithoutPrintingValue() {
        assertThatThrownBy(()->SecretsValidator.validateSetting("defuse.datasources.customers.url","postgres://demo:synthetic@host/db"))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("environment placeholder");
        assertThatCode(()->SecretsValidator.validateSetting("defuse.datasources.customers.password","${DB_PASSWORD}")).doesNotThrowAnyException();
    }
    @Test void duplicateHandlersFailStartup() {
        runner().withBean("duplicateReply",ActionHandler.class,()->new ActionHandler() {
            public String action(){return "reply_only";} public boolean movesMoney(){return false;}
            public Map<String,Object> run(ActionContext c){return Map.of();}
        }).run(c->assertThat(c).hasFailed());
    }
    @Test void directStoreRefundIsRefusedAndTicketEscalates() {
        var t=store.tickets.get("T15");var ctx=contexts.build(t.customerId);
        var d=new PolicyDecision(List.of("refund_full"),"auto",29.99,0,"low","P3",new ArrayList<>());
        assertThatThrownBy(()->billing.forProcessor("app_store").execute(new BillingCommand(t.id,"refund_full",ctx,d,t.id+":refund_full")))
                .isInstanceOf(BillingException.class);
        var results=executor.execute(t.id,d.actions(),d,ctx,"test");
        assertThat(results.get(0)).containsKey("error");assertThat(t.status).isEqualTo("escalated");
        assertThat(store.payment(ctx.lastPayment().id()).status).isEqualTo("succeeded");
    }
    @Test void readonlySourceSnapshotsCannotMutateStore() {
        var source=connectors.get("customers",CustomerSource.class);var snapshot=source.find("cus_0001").orElseThrow();
        snapshot.ltvUsd=9999;assertThat(store.customer("cus_0001").ltvUsd).isNotEqualTo(9999);
        assertThat(connectors.get("tickets",TicketSource.class).open()).hasSize(45);
    }
    @Test void pluginFailureWithoutMessageEscalatesRatherThanCrashing() {
        var handler=new ActionHandler() {
            public String action(){return "reply_only";} public boolean movesMoney(){return false;}
            public Map<String,Object> run(ActionContext c){throw new RuntimeException();}
        };
        var local=new ActionExecutorService(store,billing,new ActionRegistry(List.of(handler)),audit);
        var d=new PolicyDecision(List.of("reply_only"),"auto",0,0,"low","P4",new ArrayList<>());
        var rows=local.execute("T18",d.actions(),d,contexts.build("cus_0018"),"test");
        assertThat(rows.get(0)).containsKey("error");assertThat(store.tickets.get("T18").status).isEqualTo("escalated");
    }
    @Test void nonFiniteMoneyNeverReachesMockLedger() {
        var ctx=contexts.build("cus_0001");var d=new PolicyDecision(List.of("refund_full"),"auto",Double.NaN,0,"low","P3",new ArrayList<>());
        var rows=executor.execute("T01",d.actions(),d,ctx,"test");assertThat(rows.get(0)).containsKey("error");
        assertThat(store.payment(ctx.lastPayment().id()).status).isEqualTo("succeeded");
    }
}
