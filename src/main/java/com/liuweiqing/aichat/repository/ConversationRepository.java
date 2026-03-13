package com.liuweiqing.aichat.repository;

import com.liuweiqing.aichat.model.Conversation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ConversationRepository extends JpaRepository<Conversation, Long> {
    Optional<Conversation> findByUsernameAndConversationId(String username, String conversationId);
    List<Conversation> findByUsernameOrderByIdDesc(String username);
    void deleteByUsernameAndConversationId(String username, String conversationId);
}
