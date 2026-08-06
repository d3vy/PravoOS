package com.pravoos.common.security;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationContext;

class PiiCryptoHolderTest {

  private final PiiCryptoHolder holder = new PiiCryptoHolder();

  @AfterEach
  void resetStaticState() {
    holder.setApplicationContext(null);
  }

  @Test
  void throwsWhenContextNotInitializedYet() {
    assertThrows(IllegalStateException.class, PiiCryptoHolder::encryptor);
  }

  @Test
  void returnsEncryptorBeanFromContextAndCachesIt() {
    ApplicationContext context = mock(ApplicationContext.class);
    PiiEncryptor encryptor = new PiiEncryptor(new PiiCryptoProperties(false, null, null, null));
    when(context.getBean(PiiEncryptor.class)).thenReturn(encryptor);

    holder.setApplicationContext(context);

    assertSame(encryptor, PiiCryptoHolder.encryptor());
    assertSame(encryptor, PiiCryptoHolder.encryptor());
    verify(context, times(1)).getBean(PiiEncryptor.class);
  }

  @Test
  void reassigningContextInvalidatesCachedEncryptor() {
    ApplicationContext firstContext = mock(ApplicationContext.class);
    PiiEncryptor firstEncryptor =
        new PiiEncryptor(new PiiCryptoProperties(false, null, null, null));
    when(firstContext.getBean(PiiEncryptor.class)).thenReturn(firstEncryptor);
    holder.setApplicationContext(firstContext);
    assertSame(firstEncryptor, PiiCryptoHolder.encryptor());

    ApplicationContext secondContext = mock(ApplicationContext.class);
    PiiEncryptor secondEncryptor =
        new PiiEncryptor(new PiiCryptoProperties(false, null, null, null));
    when(secondContext.getBean(PiiEncryptor.class)).thenReturn(secondEncryptor);
    holder.setApplicationContext(secondContext);

    assertSame(secondEncryptor, PiiCryptoHolder.encryptor());
  }
}
