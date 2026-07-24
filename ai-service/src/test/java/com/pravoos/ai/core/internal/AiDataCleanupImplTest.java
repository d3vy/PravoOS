package com.pravoos.ai.core.internal;

import static org.mockito.Mockito.*;

import com.pravoos.ai.core.internal.model.mongo.Conversation;
import com.pravoos.ai.core.internal.repository.mongo.ConversationRepository;
import com.pravoos.ai.core.internal.repository.mongo.MessageRepository;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class AiDataCleanupImplTest {

  @Mock private ConversationRepository conversationRepository;
  @Mock private MessageRepository messageRepository;

  private AiDataCleanupImpl cleanup() {
    return new AiDataCleanupImpl(conversationRepository, messageRepository);
  }

  @Test
  void purgeChatDataRemovesConversationsAndTheirMessages() {
    UUID lawyerId = UUID.randomUUID();
    Conversation conversation = new Conversation(lawyerId, "title");
    ReflectionTestUtils.setField(conversation, "id", "conv-1");
    when(conversationRepository.findByLawyerId(lawyerId)).thenReturn(List.of(conversation));

    cleanup().purgeChatData(lawyerId);

    verify(messageRepository).deleteByConversationIdIn(List.of("conv-1"));
    verify(conversationRepository).deleteByLawyerId(lawyerId);
  }

  @Test
  void purgeChatDataWithoutConversationsSkipsMessageDeletion() {
    UUID lawyerId = UUID.randomUUID();
    when(conversationRepository.findByLawyerId(lawyerId)).thenReturn(List.of());

    cleanup().purgeChatData(lawyerId);

    verify(conversationRepository).deleteByLawyerId(lawyerId);
    verifyNoInteractions(messageRepository);
  }
}
