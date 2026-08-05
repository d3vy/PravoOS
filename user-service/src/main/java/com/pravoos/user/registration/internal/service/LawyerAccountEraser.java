package com.pravoos.user.registration.internal.service;

import com.pravoos.user.identity.model.enums.UserRole;
import com.pravoos.user.identity.repository.UserRepository;
import com.pravoos.user.privacy.api.AccountEraser;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class LawyerAccountEraser implements AccountEraser {

  private final UserRepository userRepository;
  private final AdminService adminService;

  public LawyerAccountEraser(UserRepository userRepository, AdminService adminService) {
    this.userRepository = userRepository;
    this.adminService = adminService;
  }

  @Override
  public boolean supports(UUID userId) {
    return userRepository
        .findById(userId)
        .map(user -> user.getRole() == UserRole.LAWYER)
        .orElse(false);
  }

  @Override
  public void erase(UUID userId) {
    adminService.deleteLawyer(userId, userId);
  }
}
