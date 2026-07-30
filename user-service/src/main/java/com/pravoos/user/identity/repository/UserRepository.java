package com.pravoos.user.identity.repository;

import com.pravoos.user.identity.model.entity.User;
import com.pravoos.user.identity.model.enums.UserRole;
import com.pravoos.user.identity.model.enums.UserStatus;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserRepository extends JpaRepository<User, UUID> {

  Optional<User> findByEmail(String email);

  Optional<User> findByEmailAndStatus(String email, UserStatus status);

  boolean existsByEmail(String email);

  boolean existsByRole(UserRole role);

  long countByRoleAndStatus(UserRole role, UserStatus status);

  long countByRoleAndStatusAndCreatedAtAfter(
      UserRole role, UserStatus status, LocalDateTime createdAt);

  @Query(
      "SELECT u FROM User u LEFT JOIN FETCH u.lawyerProfile WHERE u.role = :role AND u.status = :status")
  List<User> findByRoleAndStatusWithProfile(
      @Param("role") UserRole role, @Param("status") UserStatus status);

  @Query(
      "SELECT u FROM User u LEFT JOIN FETCH u.lawyerProfile WHERE u.role = :role AND u.status = :status ORDER BY u.createdAt DESC")
  List<User> findByRoleAndStatusWithProfile(
      @Param("role") UserRole role, @Param("status") UserStatus status, Pageable pageable);

  @Query("SELECT u FROM User u LEFT JOIN FETCH u.lawyerProfile WHERE u.id IN :ids")
  List<User> findByIdInWithProfile(@Param("ids") Collection<UUID> ids);

  @Query("SELECT u.id FROM User u WHERE u.id IN :ids AND u.digestPush = true")
  List<UUID> findIdsByIdInAndDigestPushTrue(@Param("ids") Collection<UUID> ids);
}
