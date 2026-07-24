package com.pravoos.user.collaboration.internal.repository;

import com.pravoos.user.collaboration.internal.model.entity.Organization;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrganizationRepository extends JpaRepository<Organization, UUID> {}
