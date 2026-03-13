package com.liuweiqing.aichat.repository;

import com.liuweiqing.aichat.model.Conversation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * 对话持久化仓库。
 * 继承 JpaRepository，自动获得 save()、findById()、findAll()、deleteById() 等通用 CRUD 方法，无需手动定义。
 * 下面声明的方法是 Spring Data JPA 的"方法名派生查询"——只要方法名符合命名规则，Spring 自动生成 SQL 实现。
 */
public interface ConversationRepository extends JpaRepository<Conversation, Long> {

    // Spring 解析方法名: findBy + Username + And + ConversationId
    // 自动生成: SELECT * FROM conversations WHERE username = ? AND conversation_id = ?
    Optional<Conversation> findByUsernameAndConversationId(String username, String conversationId);

    // 自动生成: SELECT * FROM conversations WHERE username = ? ORDER BY id DESC
    List<Conversation> findByUsernameOrderByIdDesc(String username);

    // 自动生成: DELETE FROM conversations WHERE username = ? AND conversation_id = ?
    void deleteByUsernameAndConversationId(String username, String conversationId);
}
