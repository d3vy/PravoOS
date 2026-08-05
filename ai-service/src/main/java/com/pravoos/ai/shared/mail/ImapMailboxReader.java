package com.pravoos.ai.shared.mail;

import com.pravoos.ai.shared.config.MailAttachmentProperties;
import com.pravoos.ai.shared.config.MailSyncProperties;
import com.pravoos.ai.shared.config.MailboxProperties;
import com.pravoos.ai.shared.exception.EmailMessageUnavailableException;
import com.pravoos.ai.shared.exception.MailboxConnectionException;
import jakarta.mail.AuthenticationFailedException;
import jakarta.mail.FetchProfile;
import jakarta.mail.Folder;
import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.Session;
import jakarta.mail.Store;
import jakarta.mail.UIDFolder;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.SortedMap;
import java.util.TreeMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ImapMailboxReader implements MailboxReader {

  private static final Logger log = LoggerFactory.getLogger(ImapMailboxReader.class);

  private final MailboxProperties properties;
  private final FetchedEmailMapper fetchedEmailMapper;
  private final AttachmentExtractor attachmentExtractor;

  public ImapMailboxReader(
      MailboxProperties properties,
      MailSyncProperties syncProperties,
      MailAttachmentProperties attachmentProperties) {
    this.properties = properties;
    this.fetchedEmailMapper = new FetchedEmailMapper(syncProperties.maxBodyLength());
    this.attachmentExtractor =
        new AttachmentExtractor(
            attachmentProperties.maxPerMessage(), attachmentProperties.maxBytes());
  }

  @Override
  public void verifyConnection(MailboxCredentials credentials) {
    Store store = null;
    Folder folder = null;
    try {
      store = openStore(credentials);
      folder = openFolder(store, credentials.folder());
    } catch (AuthenticationFailedException e) {
      throw new MailboxConnectionException("Неверный логин или пароль приложения", e);
    } catch (MessagingException e) {
      throw new MailboxConnectionException(
          "Не удалось подключиться к IMAP-серверу: " + rootMessage(e), e);
    } finally {
      closeQuietly(folder, store);
    }
  }

  @Override
  public MailboxFetchResult fetchMessages(
      MailboxCredentials credentials, MailboxSyncCursor cursor, int maxMessages) {
    Store store = null;
    Folder folder = null;
    try {
      store = openStore(credentials);
      folder = openFolder(store, credentials.folder());
      UIDFolder uidFolder = asUidFolder(folder, credentials.folder());

      long uidValidity = uidFolder.getUIDValidity();
      boolean reindexed = cursor.uidValidity() != null && !cursor.matches(uidValidity);
      long startUid = cursor.startUid(uidValidity);
      long lastSeenUid = startUid - 1;
      if (folder.getMessageCount() == 0) {
        return MailboxFetchResult.empty(uidValidity, lastSeenUid, reindexed);
      }

      SortedMap<Long, Message> fresh = freshMessages(uidFolder, startUid, maxMessages);
      if (fresh.isEmpty()) {
        return MailboxFetchResult.empty(uidValidity, lastSeenUid, reindexed);
      }
      prefetch(folder, fresh.values());

      List<FetchedEmail> emails = new ArrayList<>(fresh.size());
      for (var entry : fresh.entrySet()) {
        long uid = entry.getKey();
        try {
          emails.add(
              fetchedEmailMapper.map(entry.getValue(), uid, uidValidity, credentials.host()));
        } catch (MessagingException | IOException e) {
          log.warn(
              "Письмо uid={} ящика {} пропущено: {}", uid, credentials.username(), e.getMessage());
        }
        lastSeenUid = uid;
      }
      return new MailboxFetchResult(uidValidity, lastSeenUid, reindexed, emails);
    } catch (AuthenticationFailedException e) {
      throw new MailboxConnectionException("Неверный логин или пароль приложения", e);
    } catch (MessagingException e) {
      throw new MailboxConnectionException("Не удалось прочитать почту: " + rootMessage(e), e);
    } finally {
      closeQuietly(folder, store);
    }
  }

  @Override
  public List<FetchedAttachment> fetchAttachments(
      MailboxCredentials credentials, MailboxSyncCursor cursor, long uid) {
    Store store = null;
    Folder folder = null;
    try {
      store = openStore(credentials);
      folder = openFolder(store, credentials.folder());
      UIDFolder uidFolder = asUidFolder(folder, credentials.folder());
      if (cursor.uidValidity() != null && !cursor.matches(uidFolder.getUIDValidity())) {
        throw new EmailMessageUnavailableException(uid);
      }

      Message message = uidFolder.getMessageByUID(uid);
      if (message == null) {
        throw new EmailMessageUnavailableException(uid);
      }
      return attachmentExtractor.extract(message);
    } catch (AuthenticationFailedException e) {
      throw new MailboxConnectionException("Неверный логин или пароль приложения", e);
    } catch (MessagingException | IOException e) {
      throw new MailboxConnectionException(
          "Не удалось загрузить вложения письма: " + rootMessage(e), e);
    } finally {
      closeQuietly(folder, store);
    }
  }

  private SortedMap<Long, Message> freshMessages(
      UIDFolder uidFolder, long startUid, int maxMessages) throws MessagingException {
    Message[] candidates = uidFolder.getMessagesByUID(startUid, UIDFolder.LASTUID);
    SortedMap<Long, Message> byUid = new TreeMap<>();
    for (Message candidate : candidates) {
      long uid = uidFolder.getUID(candidate);
      if (uid >= startUid) {
        byUid.put(uid, candidate);
      }
    }
    if (byUid.size() <= maxMessages) {
      return byUid;
    }
    SortedMap<Long, Message> limited = new TreeMap<>();
    for (var entry : byUid.entrySet()) {
      limited.put(entry.getKey(), entry.getValue());
      if (limited.size() == maxMessages) {
        break;
      }
    }
    return limited;
  }

  private void prefetch(Folder folder, java.util.Collection<Message> messages)
      throws MessagingException {
    FetchProfile profile = new FetchProfile();
    profile.add(FetchProfile.Item.ENVELOPE);
    profile.add(UIDFolder.FetchProfileItem.UID);
    profile.add("Message-ID");
    profile.add("In-Reply-To");
    profile.add("References");
    folder.fetch(messages.toArray(new Message[0]), profile);
  }

  private Folder openFolder(Store store, String folderName) throws MessagingException {
    Folder folder = store.getFolder(folderName);
    if (!folder.exists()) {
      throw new MailboxConnectionException(
          "Папка '" + folderName + "' не найдена на сервере", null);
    }
    folder.open(Folder.READ_ONLY);
    return folder;
  }

  private UIDFolder asUidFolder(Folder folder, String folderName) {
    if (folder instanceof UIDFolder uidFolder) {
      return uidFolder;
    }
    throw new MailboxConnectionException(
        "Сервер не поддерживает UID для папки '" + folderName + "'", null);
  }

  private Store openStore(MailboxCredentials credentials) throws MessagingException {
    String protocol = credentials.ssl() ? "imaps" : "imap";
    Properties props = new Properties();
    props.put("mail.store.protocol", protocol);
    props.put("mail.%s.host".formatted(protocol), credentials.host());
    props.put("mail.%s.port".formatted(protocol), String.valueOf(credentials.port()));
    props.put(
        "mail.%s.connectiontimeout".formatted(protocol),
        String.valueOf(properties.connectTimeout().toMillis()));
    props.put(
        "mail.%s.timeout".formatted(protocol), String.valueOf(properties.readTimeout().toMillis()));
    props.put(
        "mail.%s.writetimeout".formatted(protocol),
        String.valueOf(properties.readTimeout().toMillis()));
    if (credentials.ssl()) {
      props.put("mail.imaps.ssl.enable", "true");
      props.put("mail.imaps.ssl.checkserveridentity", "true");
    } else {
      props.put("mail.imap.starttls.enable", "true");
    }

    Store store = Session.getInstance(props).getStore(protocol);
    store.connect(
        credentials.host(), credentials.port(), credentials.username(), credentials.password());
    return store;
  }

  private String rootMessage(Throwable throwable) {
    Throwable current = throwable;
    while (current.getCause() != null) {
      current = current.getCause();
    }
    return current.getMessage() == null ? current.getClass().getSimpleName() : current.getMessage();
  }

  private void closeQuietly(Folder folder, Store store) {
    if (folder != null && folder.isOpen()) {
      try {
        folder.close(false);
      } catch (MessagingException e) {
        log.debug("Не удалось закрыть IMAP-папку: {}", e.getMessage());
      }
    }
    if (store != null && store.isConnected()) {
      try {
        store.close();
      } catch (MessagingException e) {
        log.debug("Не удалось закрыть IMAP-соединение: {}", e.getMessage());
      }
    }
  }
}
