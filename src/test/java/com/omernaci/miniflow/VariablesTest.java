package com.omernaci.miniflow;

import com.omernaci.miniflow.common.exception.MiniFlowException;
import com.omernaci.miniflow.engine.ExecutionContext;
import com.omernaci.miniflow.engine.WorkflowEngine;
import com.omernaci.miniflow.variable.VariableService;
import com.omernaci.miniflow.workflow.Workflow;
import com.omernaci.miniflow.workflow.Workflow.Connection;
import com.omernaci.miniflow.workflow.Workflow.NodeDef;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
class VariablesTest {

    @Autowired
    VariableService variables;

    @Autowired
    WorkflowEngine engine;

    @Autowired
    MockMvc mvc;

    private Object eval(String expression) {
        Workflow wf = new Workflow("var-test", "x", List.of(
                new NodeDef("s", "trigger", Map.of()),
                new NodeDef("v", "set", Map.of("keepInput", false, "values", Map.of("r", expression)))
        ), List.of(new Connection("s", "v", null)));
        ExecutionContext ctx = engine.run(wf, Map.of());
        return ((Map<?, ?>) ctx.nodeOutputs().get("v")).get("r");
    }

    @Test
    void expressionsMayContainNewlinesAndSeveralExpressionsAreNotOneExpression() {
        assertThat(eval("{{ {'a', 'b'}.toString().replace('[', '').replace(']', '').replace(', ', '\n- ') }}"))
                .isEqualTo("a\n- b");
        assertThat(eval("Top:\n{{ 'x' + '\n' + 'y' }}\nEnd")).isEqualTo("Top:\nx\ny\nEnd");
        assertThat(eval("{{ 1 + 1 }} and {{ 2 + 2 }}")).isEqualTo("2 and 4");
        assertThat(eval("{{ 1 > 0 ? 'ok' : 'no' }} report\n{{ 'line' }}")).isEqualTo("ok report\nline");
        assertThat(eval("  {{ 40 + 2 }}  ")).isEqualTo(42);
    }

    @Test
    void propertiesAreSeededIntoTableOnFirstStart() {
        assertThat(variables.list()).anyMatch(r -> r.name().equals("greeting") && r.fromDb());
        assertThat(eval("{{ vars.greeting }}")).isEqualTo("Merhaba");
    }

    @Test
    void changesApplyImmediatelyWithoutRestart() {
        variables.save(null, "esik", "40", "Kur alarmı eşiği");
        assertThat(eval("{{ vars.esik }}")).isEqualTo("40");

        variables.save("esik", "esikDegeri", "45", null);
        assertThat(eval("{{ vars.esik }}")).isNull();
        assertThat(eval("{{ vars.esikDegeri }}")).isEqualTo("45");
        assertThat(eval("{{ vars.ESIKDEGERI }}")).isEqualTo("45");

        assertThat(variables.delete("esikDegeri")).isTrue();
        assertThat(eval("{{ vars.esikDegeri }}")).isNull();
    }

    @Test
    void validation() {
        assertThatThrownBy(() -> variables.save(null, "tireli-ad", "x", null)).isInstanceOf(MiniFlowException.class);
        assertThatThrownBy(() -> variables.save(null, "1rakamla", "x", null)).isInstanceOf(MiniFlowException.class);
        variables.save(null, "tekDeger", "a", null);
        assertThatThrownBy(() -> variables.save(null, "TEKDEGER", "b", null))
                .isInstanceOf(MiniFlowException.class).hasMessageContaining("TEKDEGER");
    }

    @Test
    void variablesPage() throws Exception {
        mvc.perform(post("/variables").param("name", "sayfaTest").param("value", "deger-123").param("description", "açıklama"))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attributeExists("success"));

        mvc.perform(get("/variables"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("sayfaTest")))
                .andExpect(content().string(containsString("deger-123")))
                .andExpect(content().string(containsString("{{ vars.sayfaTest }}")));

        mvc.perform(get("/variables").param("edit", "sayfaTest"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("value=\"sayfaTest\"")));

        mvc.perform(post("/variables").param("name", "gecersiz-ad"))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attributeExists("error"))
                .andExpect(flash().attributeExists("form"));

        mvc.perform(post("/variables/sayfaTest/delete"))
                .andExpect(status().is3xxRedirection());
        assertThat(variables.get("sayfaTest")).isEmpty();
    }
}
