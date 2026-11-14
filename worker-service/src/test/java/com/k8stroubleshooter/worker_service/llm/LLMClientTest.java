package com.k8stroubleshooter.worker_service.llm;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.k8stroubleshooter.worker_service.config.LLMConfigProperties;
import com.k8s_troubleshooter.common.entity.PodSnapshot;
import com.k8stroubleshooter.worker_service.llm.dto.LLMResponse;
import com.k8s_troubleshooter.common.FailureCategory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class LLMClientTest {

    private static final String ENDPOINT = "https://llm.test/chat/completions";
    private static final String ROOT_CAUSE = "The application cannot reach its database.";
    private static final String SUGGESTED_FIX = "Check the database service and connection settings.";

    private final ObjectMapper objectMapper = new ObjectMapper();
    private MockRestServiceServer server;
    private LLMClient client;
    private String prompt;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        client = new LLMClient(builder.build(),
                new LLMConfigProperties(ENDPOINT, "test-key", "test-model"));
        PodSnapshot snapshot = new PodSnapshot("api-pod", "payments", "Running",
                "CrashLoopBackOff", 6, List.of("Back-off restarting failed container"),
                "ERROR Connection refused");
        prompt = new PromptBuilder().buildPrompt(snapshot, snapshot.rawLogs());
    }

    @ParameterizedTest
    @EnumSource(FailureCategory.class)
    void readsEveryCategoryRequestedByThePrompt(FailureCategory category) throws Exception {
        assertThat(prompt).contains("\"category\": string");
        expectCompletion("category", category.name());

        LLMResponse response = client.llmCall(prompt);

        assertThat(response.failureCategory()).isEqualTo(category);
        assertThat(response.rootCause()).isEqualTo(ROOT_CAUSE);
        assertThat(response.suggestedFix()).isEqualTo(SUGGESTED_FIX);
        server.verify();
    }

    @Test
    void acceptsLegacyFailureCategoryField() throws Exception {
        expectCompletion("failureCategory", "ConfigError");

        LLMResponse response = client.llmCall(prompt);

        assertThat(response.failureCategory()).isEqualTo(FailureCategory.ConfigError);
        assertThat(response.rootCause()).isEqualTo(ROOT_CAUSE);
        assertThat(response.suggestedFix()).isEqualTo(SUGGESTED_FIX);
        server.verify();
    }

    @Test
    void rejectsAnUnsupportedCategoryInsteadOfSilentlyDroppingIt() throws Exception {
        expectCompletion("category", "UnsupportedCategory");

        assertThatThrownBy(() -> client.llmCall(prompt))
                .isInstanceOf(JsonProcessingException.class);
        server.verify();
    }

    private void expectCompletion(String categoryField, String category) throws JsonProcessingException {
        String diagnosis = objectMapper.writeValueAsString(Map.of(
                categoryField, category,
                "rootCause", ROOT_CAUSE,
                "suggestedFix", SUGGESTED_FIX));
        String completion = objectMapper.writeValueAsString(Map.of(
                "id", "chatcmpl-test",
                "model", "test-model",
                "choices", List.of(Map.of(
                        "index", 0,
                        "message", Map.of("role", "assistant", "content", diagnosis),
                        "finish_reason", "stop"))));
        String expectedRequest = objectMapper.writeValueAsString(Map.of(
                "model", "test-model",
                "messages", List.of(Map.of("role", "user", "content", prompt))));

        server.expect(requestTo(ENDPOINT))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("authorization", "bearer test-key"))
                .andExpect(content().json(expectedRequest))
                .andRespond(withSuccess(completion, MediaType.APPLICATION_JSON));
    }
}

