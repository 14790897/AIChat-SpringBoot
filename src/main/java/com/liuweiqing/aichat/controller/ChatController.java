package com.liuweiqing.aichat.controller;

import com.liuweiqing.aichat.model.Conversation;
import com.liuweiqing.aichat.repository.ConversationRepository;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private final WebClient webClient;
    private final String model;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ConversationRepository conversationRepository;

    public ChatController(
            @Value("${spring.ai.openai.base-url}") String baseUrl,
            @Value("${spring.ai.openai.api-key}") String apiKey,
            @Value("${spring.ai.openai.chat.options.model}") String model,
            ConversationRepository conversationRepository) {
        this.model = model;
        this.conversationRepository = conversationRepository;
        this.webClient = WebClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader("Authorization", "Bearer " + apiKey)
                .defaultHeader("Content-Type", "application/json")
                .build();
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, String>> loadHistory(String username, String conversationId) {
        return conversationRepository.findByUsernameAndConversationId(username, conversationId)
                .map(conv -> {
                    try {
                        return (List<Map<String, String>>) objectMapper.readValue(
                                conv.getMessagesJson(), List.class);
                    } catch (Exception e) {
                        return newHistory();
                    }
                })
                .orElseGet(this::newHistory);
    }

    private List<Map<String, String>> newHistory() {
        List<Map<String, String>> messages = new ArrayList<>();
        messages.add(Map.of("role", "system", "content", "You are a helpful AI assistant."));
        return messages;
    }

    private void saveHistory(String username, String conversationId, List<Map<String, String>> history) {
        try {
            String json = objectMapper.writeValueAsString(history);
            Conversation conv = conversationRepository.findByUsernameAndConversationId(username, conversationId)
                    .orElse(new Conversation(username, conversationId, json));
            conv.setMessagesJson(json);
            conversationRepository.save(conv);
        } catch (Exception e) {
            log.error("Failed to save conversation: {}", e.getMessage());
        }
    }

    @PostMapping("/send")
    public String chat(@RequestBody ChatRequest request, Authentication authentication) {
        String username = authentication.getName();
        log.info("User [{}] chat request: {}", username, request.message());

        String convId = request.conversationId() != null ? request.conversationId() : "default";
        List<Map<String, String>> history = loadHistory(username, convId);
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
        log.info("User [{}] chat response: {}", username, content);

        history.add(Map.of("role", "assistant", "content", content));
        saveHistory(username, convId, history);
        return content;
    }

    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter chatStream(@RequestBody ChatRequest request, Authentication authentication) {
        String username = authentication.getName();
        log.info("User [{}] stream chat request: {}", username, request.message());

        String convId = request.conversationId() != null ? request.conversationId() : "default";
        List<Map<String, String>> history = loadHistory(username, convId);
        history.add(Map.of("role", "user", "content", request.message()));

        Map<String, Object> body = Map.of(
                "model", model,
                "stream", true,
                "messages", history);

        SseEmitter emitter = new SseEmitter(180_000L);
        StringBuilder fullResponse = new StringBuilder();

        webClient.post()
                .uri("/chat/completions")
                .accept(MediaType.TEXT_EVENT_STREAM)
                .bodyValue(body)
                .retrieve()
                .bodyToFlux(String.class)
                .filter(chunk -> !"[DONE]".equals(chunk.trim()))
                .subscribe(
                        chunk -> {
                            try {
                                JsonNode node = objectMapper.readTree(chunk);
                                JsonNode content = node.at("/choices/0/delta/content");
                                if (content.isMissingNode() || content.isNull())
                                    return;
                                String text = content.asString();
                                fullResponse.append(text);
                                emitter.send(SseEmitter.event().data(text));
                            } catch (Exception e) {
                                // skip unparseable chunks
                            }
                        },
                        error -> {
                            log.error("Stream response error: {}", error.getMessage());
                            emitter.completeWithError(error);
                        },
                        () -> {
                            log.info("User [{}] stream response: {}", username, fullResponse);
                            history.add(Map.of("role", "assistant", "content", fullResponse.toString()));
                            saveHistory(username, convId, history);
                            emitter.complete();
                        }
                );

        return emitter;
    }

    @PostMapping("/clear")
    @Transactional
    public String clearHistory(@RequestBody(required = false) ClearRequest request, Authentication authentication) {
        String username = authentication.getName();
        String convId = request != null && request.conversationId() != null ? request.conversationId() : "default";
        conversationRepository.deleteByUsernameAndConversationId(username, convId);
        log.info("User [{}] cleared conversation: {}", username, convId);
        return "Conversation cleared";
    }

    @GetMapping("/conversations")
    public List<Map<String, String>> listConversations(Authentication authentication) {
        String username = authentication.getName();
        return conversationRepository.findByUsernameOrderByIdDesc(username).stream()
                .map(conv -> {
                    String title = conv.getConversationId();
                    try {
                        @SuppressWarnings("unchecked")
                        List<Map<String, String>> msgs = (List<Map<String, String>>) objectMapper.readValue(
                                conv.getMessagesJson(), List.class);
                        for (Map<String, String> msg : msgs) {
                            if ("user".equals(msg.get("role"))) {
                                String content = msg.get("content");
                                title = content.length() > 30 ? content.substring(0, 30) + "..." : content;
                                break;
                            }
                        }
                    } catch (Exception ignored) {}
                    return Map.of("conversationId", conv.getConversationId(), "title", title);
                })
                .toList();
    }

    @GetMapping("/conversation")
    public List<Map<String, String>> getConversation(
            @RequestParam String conversationId, Authentication authentication) {
        String username = authentication.getName();
        return conversationRepository.findByUsernameAndConversationId(username, conversationId)
                .map(conv -> {
                    try {
                        @SuppressWarnings("unchecked")
                        List<Map<String, String>> msgs = (List<Map<String, String>>) objectMapper.readValue(
                                conv.getMessagesJson(), List.class);
                        return msgs.stream()
                                .filter(m -> !"system".equals(m.get("role")))
                                .toList();
                    } catch (Exception e) {
                        return List.<Map<String, String>>of();
                    }
                })
                .orElse(List.of());
    }

    public record ChatRequest(String message, String conversationId) {}
    public record ClearRequest(String conversationId) {}
}
