package com.distributed.teamai.intelligence_service.repository;

import com.distributed.teamai.intelligence_service.entity.ChatSession;
import com.distributed.teamai.intelligence_service.entity.ChatSessionId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ChatSessionRepository extends JpaRepository<ChatSession, ChatSessionId> {
}
