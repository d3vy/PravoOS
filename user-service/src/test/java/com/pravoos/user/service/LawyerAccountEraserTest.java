package com.pravoos.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.pravoos.user.identity.model.entity.User;
import com.pravoos.user.identity.model.enums.UserRole;
import com.pravoos.user.identity.repository.UserRepository;
import com.pravoos.user.registration.internal.service.AdminService;
import com.pravoos.user.registration.internal.service.LawyerAccountEraser;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LawyerAccountEraserTest {

  @Mock private UserRepository userRepository;
  @Mock private AdminService adminService;

  private LawyerAccountEraser eraser;

  @BeforeEach
  void setUp() {
    eraser = new LawyerAccountEraser(userRepository, adminService);
  }

  @Test
  void supports_returnsTrue_whenUserIsLawyer() {
    UUID userId = UUID.randomUUID();
    when(userRepository.findById(userId)).thenReturn(Optional.of(userWithRole(UserRole.LAWYER)));

    assertThat(eraser.supports(userId)).isTrue();
  }

  @Test
  void supports_returnsFalse_whenUserIsNotLawyer() {
    UUID userId = UUID.randomUUID();
    when(userRepository.findById(userId)).thenReturn(Optional.of(userWithRole(UserRole.CLIENT)));

    assertThat(eraser.supports(userId)).isFalse();
  }

  @Test
  void supports_returnsFalse_whenUserDoesNotExist() {
    UUID userId = UUID.randomUUID();
    when(userRepository.findById(userId)).thenReturn(Optional.empty());

    assertThat(eraser.supports(userId)).isFalse();
  }

  @Test
  void erase_delegatesToAdminService_withUserIdAsBothUserAndAdmin() {
    UUID userId = UUID.randomUUID();

    eraser.erase(userId);

    verify(adminService).deleteLawyer(userId, userId);
  }

  @Test
  void erase_doesNotTouchUserRepository() {
    UUID userId = UUID.randomUUID();

    eraser.erase(userId);

    verify(userRepository, never()).findById(userId);
  }

  private User userWithRole(UserRole role) {
    User user = new User();
    user.setRole(role);
    return user;
  }
}
