package com.pravoos.ai.practice.internal.recyclebin;

import com.pravoos.ai.practice.internal.model.entity.Mailbox;
import com.pravoos.ai.practice.internal.repository.jpa.MailboxRepository;
import com.pravoos.ai.recyclebin.api.AbstractLeafSoftDeleteStore;
import com.pravoos.ai.recyclebin.api.BinSnapshot;
import com.pravoos.ai.recyclebin.api.RecycleBinEntityType;
import com.pravoos.ai.shared.exception.MailboxNotFoundException;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class MailboxSoftDeleteStore
    extends AbstractLeafSoftDeleteStore<Mailbox, MailboxRepository> {

  public MailboxSoftDeleteStore(MailboxRepository mailboxRepository) {
    super(mailboxRepository);
  }

  @Override
  public RecycleBinEntityType entityType() {
    return RecycleBinEntityType.MAILBOX;
  }

  @Override
  protected RuntimeException notFoundException(UUID id) {
    return new MailboxNotFoundException(id);
  }

  @Override
  protected BinSnapshot snapshot(Mailbox mailbox) {
    return new BinSnapshot(
        RecycleBinEntityType.MAILBOX,
        mailbox.getId().toString(),
        mailbox.getEmailAddress(),
        mailbox.getUserId(),
        null,
        Map.of());
  }
}
