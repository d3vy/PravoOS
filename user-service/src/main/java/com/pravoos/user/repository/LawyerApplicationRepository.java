package com.pravoos.user.repository;

import com.pravoos.user.model.entity.LawyerApplication;
import com.pravoos.user.model.enums.ApplicationStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface LawyerApplicationRepository extends JpaRepository<LawyerApplication, UUID> {

    List<LawyerApplication> findByStatusOrderBySubmittedAtDesc(ApplicationStatus status);

    List<LawyerApplication> findAllByOrderBySubmittedAtDesc();

    boolean existsByEmailAndStatus(String email, ApplicationStatus status);
}
