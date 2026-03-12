package com.liuweiqing.aichat.controller;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private final WebClient webClient;
    private final String model;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ConcurrentHashMap<String, List<Map<String, String>>> conversations = new ConcurrentHashMap<>();

    public ChatController(
            @Value("${spring.ai.openai.base-url}") String baseUrl,
            @Value("${spring.ai.openai.api-key}") String apiKey,
            @Value("${spring.ai.openai.chat.options.model}") String model) {
        this.model = model;
        this.webClient = WebClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader("Authorization", "Bearer " + apiKey)
                .defaultHeader("Content-Type", "application/json")
                .build();
    }

    private List<Map<String, String>> getHistory(String conversationId) {
        return conversations.computeIfAbsent(conversationId, k -> {
            List<Map<String, String>> messages = new ArrayList<>();
            messages.add(Map.of("role", "system", "content", "You are a helpful AI assistant."));
            return messages;
        });
    }

    @PostMapping("/send")
    public String chat(@RequestBody ChatRequest request) {
        log.info("Received chat request: {}", request.message());

        String convId = request.conversationId() != null ? request.conversationId() : "default";
        List<Map<String, String>> history = getHistory(convId);
        history.add(Map.of("role", "user", "content", request.message()));

        Map<String, Object> body = Map.of(
                "model", model,
                "messages", history);

        JsonNode response = webClient.post()
                .uri("/chat/completions")
                .bodyValue(body)
                .retrieve()
                .bodyToMono(JsonNode.class)
                .block();

        String content = response.at("/choices/0/message/content").asString();
        log.info("Chat response: {}", content);

        history.add(Map.of("role", "assistant", "content", content));
        return content;
    }

    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> chatStream(@RequestBody ChatRequest request) {
        log.info("Received stream chat request: {}", request.message());

        String convId = request.conversationId() != null ? request.conversationId() : "default";
        List<Map<String, String>> history = getHistory(convId);
        history.add(Map.of("role", "user", "content", request.message()));

        Map<String, Object> body = Map.of(
                "model", model,
                "stream", true,
                "messages", history);

        StringBuilder fullResponse = new StringBuilder();

        return webClient.post()
                .uri("/chat/completions")
                .accept(MediaType.TEXT_EVENT_STREAM)
                .bodyValue(body)
                .retrieve()
                .bodyToFlux(String.class)
                .filter(chunk -> !"[DONE]".equals(chunk.trim()))
                .<ServerSentEvent<String>>mapNotNull(chunk -> {
                    try {
                        JsonNode node = objectMapper.readTree(chunk);
                        JsonNode content = node.at("/choices/0/delta/content");
                        if (content.isMissingNode() || content.isNull())
                            return null;
                        String text = content.asString();
                        fullResponse.append(text);
                        return ServerSentEvent.<String>builder()
                                .data(text)
                                .build();
                    } catch (Exception e) {
                        return null;
                    }
                })
                .doOnComplete(() -> {
                    log.info("Stream response: {}", fullResponse);
                    history.add(Map.of("role", "assistant", "content", fullResponse.toString()));
                })
                .doOnError(e -> log.error("Stream response error: {}", e.getMessage()));
    }

    @PostMapping("/clear")
    public String clearHistory(@RequestBody(required = false) ClearRequest request) {
        String convId = request != null && request.conversationId() != null ? request.conversationId() : "default";
        conversations.remove(convId);
        log.info("Cleared conversation: {}", convId);
        return "Conversation cleared";
    }

    public record ChatRequest(String message, String conversationId) {}
    public record ClearRequest(String conversationId) {}
}
