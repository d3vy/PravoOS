package com.pravoos.ai.core.internal.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.pravoos.ai.core.internal.dto.AdminConversationResponse;
import com.pravoos.ai.core.internal.dto.MessageResponse;
import com.pravoos.ai.core.internal.model.mongo.Conversation;
import com.pravoos.ai.core.internal.model.mongo.Message;
import com.pravoos.ai.core.internal.repository.mongo.ConversationRepository;
import com.pravoos.ai.core.internal.repository.mongo.MessageRepository;
import com.pravoos.ai.shared.exception.ConversationNotFoundException;
import com.pravoos.ai.shared.model.enums.MessageRole;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class AdminConversationServiceTest {

  @Mock private ConversationRepository conversationRepository;
  @Mock private MessageRepository messageRepository;

  @InjectMocks private AdminConversationService service;

  @Test
  void listsConversationsFilteredByOrgAndLawyer() {
    UUID orgId = UUID.randomUUID();
    UUID lawyerId = UUID.randomUUID();
    Conversation conversation = new Conversation(lawyerId, orgId, "Иск", null, null);
    ReflectionTestUtils.setField(conversation, "id", "conv-1");
    when(conversationRepository.searchForAdmin(eq(orgId), eq(lawyerId), eq("иск"), any()))
        .thenReturn(new PageImpl<>(List.of(conversation)));

    Page<AdminConversationResponse> result =
        service.getConversations(orgId, lawyerId, "иск", 0, 20);

    assertThat(result.getContent()).hasSize(1);
    assertThat(result.getContent().get(0).orgId()).isEqualTo(orgId);
    assertThat(result.getContent().get(0).updatedAt()).isNotNull();
  }

  @Test
  void returnsMessagesInChronologicalOrder() {
    Conversation conversation = new Conversation(UUID.randomUUID(), null, "Иск", null, null);
    ReflectionTestUtils.setField(conversation, "id", "conv-1");
    when(conversationRepository.findActiveById("conv-1")).thenReturn(Optional.of(conversation));
    Message newer = new Message("conv-1", MessageRole.ASSISTANT, "Ответ", List.of());
    Message older = new Message("conv-1", MessageRole.USER, "Вопрос", List.of());
    when(messageRepository.findByConversationIdOrderByCreatedAtDesc(eq("conv-1"), any()))
        .thenReturn(new PageImpl<>(List.of(newer, older)));

    Page<MessageResponse> result = service.getMessages("conv-1", 0, 50);

    assertThat(result.getContent())
        .extracting(MessageResponse::content)
        .containsExactly("Вопрос", "Ответ");
  }

  @Test
  void rejectsDeletedOrMissingConversation() {
    when(conversationRepository.findActiveById("gone")).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.getMessages("gone", 0, 50))
        .isInstanceOf(ConversationNotFoundException.class);
    verify(messageRepository, org.mockito.Mockito.never())
        .findByConversationIdOrderByCreatedAtDesc(any(), any());
  }

  @Test
  void listsAllConversationsWhenNoFilters() {
    when(conversationRepository.searchForAdmin(isNull(), isNull(), isNull(), any()))
        .thenReturn(Page.empty());

    assertThat(service.getConversations(null, null, null, 0, 20).getContent()).isEmpty();
  }
}
