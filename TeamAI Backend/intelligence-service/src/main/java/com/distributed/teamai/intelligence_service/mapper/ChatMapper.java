package com.distributed.teamai.intelligence_service.mapper;

import com.distributed.teamai.intelligence_service.dto.chat.ChatEventResponse;
import com.distributed.teamai.intelligence_service.dto.chat.ChatResponse;
import com.distributed.teamai.intelligence_service.entity.ChatEvent;
import com.distributed.teamai.intelligence_service.entity.ChatMessage;
import org.mapstruct.Mapper;

import java.util.List;

@Mapper(componentModel = "spring")
public interface ChatMapper {

    List<ChatResponse> toChatResponse(List<ChatMessage> chatMessageList);

    ChatEventResponse toChatEventResponse(ChatEvent chatEvent);

}
