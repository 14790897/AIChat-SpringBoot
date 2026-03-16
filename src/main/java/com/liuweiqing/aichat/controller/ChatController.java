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

/**
 * 聊天控制器，处理所有 /api/chat/** 请求。
 * 注意：使用 SseEmitter（servlet 原生 SSE）而非 Flux<ServerSentEvent>，
 * 因为在 servlet-based Spring MVC + Spring Security 环境下，Flux 完成时会触发
 * async dispatch 通过安全过滤链，而此时 response 已提交、JWT 上下文丢失，导致 Access Denied。
 * SseEmitter 配合 SecurityConfig 中 dispatcherTypeMatchers(ASYNC).permitAll() 解决此问题。
 */
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

    // upsert 逻辑：数据库中有则更新，无则新建
    private void saveHistory(String username, String conversationId, List<Map<String, String>> history) {
        try {
            String json = objectMapper.writeValueAsString(history);
            // Optional.orElse: 查到已有对话就用它，查不到就 new 一个新的
            Conversation conv = conversationRepository.findByUsernameAndConversationId(username, conversationId)
                    .orElse(new Conversation(username, conversationId, json));
            conv.setMessagesJson(json);
            conversationRepository.save(conv); // save() 继承自 JpaRepository，无需在 Repository 中定义
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

    /**
     * SSE 流式聊天接口。
     * 使用 SseEmitter 而非 Flux<ServerSentEvent>，避免 async dispatch 触发 Spring Security Access Denied。
     * WebClient 仍以响应式方式调用 OpenAI API，通过 subscribe() 将数据推送到 SseEmitter。
     */
    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter chatStream(@RequestBody ChatRequest request, Authentication authentication) {
        String username = authentication.getName();
        log.info("User [{}] stream chat request: {}", username, request.message());
        String convId = request.conversationId() != null ? request.conversationId() : "default";
        log.info("Conversation ID: {}", convId);
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
