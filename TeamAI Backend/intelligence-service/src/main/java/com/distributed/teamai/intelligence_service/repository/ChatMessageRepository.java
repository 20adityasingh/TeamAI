package com.distributed.teamai.intelligence_service.repository;

import com.distributed.teamai.intelligence_service.entity.ChatMessage;
import com.distributed.teamai.intelligence_service.entity.ChatSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {

    @Query("""
            select distinct(m) from ChatMessage m
                        left join fetch m.events e
                                    where m.chatSession = :chatSession
                                                order by m.createdAt asc, e.sequenceOrder asc
            """)
    List<ChatMessage> findByChatSession(@Param("chatSession") ChatSession chatSession);

}
