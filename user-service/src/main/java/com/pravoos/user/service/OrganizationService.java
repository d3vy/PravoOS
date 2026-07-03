package com.pravoos.user.service;

import com.pravoos.user.exception.LawyerNotFoundException;
import com.pravoos.user.exception.NotOrganizationMemberException;
import com.pravoos.user.exception.OrganizationAccessDeniedException;
import com.pravoos.user.exception.OrganizationNotFoundException;
import com.pravoos.user.exception.PravoosException;
import com.pravoos.user.model.dto.CreateOrganizationRequest;
import com.pravoos.user.model.dto.OrganizationMemberResponse;
import com.pravoos.user.model.dto.OrganizationResponse;
import com.pravoos.user.model.entity.Organization;
import com.pravoos.user.model.entity.OrganizationMembership;
import com.pravoos.user.model.entity.User;
import com.pravoos.user.model.enums.OrgRole;
import com.pravoos.user.model.enums.UserRole;
import com.pravoos.user.repository.OrganizationMembershipRepository;
import com.pravoos.user.repository.OrganizationRepository;
import com.pravoos.user.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class OrganizationService {

    private static final Logger log = LoggerFactory.getLogger(OrganizationService.class);

    private final OrganizationRepository organizationRepository;
    private final OrganizationMembershipRepository membershipRepository;
    private final UserRepository userRepository;
    private final OrganizationAccessGuard accessGuard;

    public OrganizationService(OrganizationRepository organizationRepository,
                               OrganizationMembershipRepository membershipRepository,
                               UserRepository userRepository,
                               OrganizationAccessGuard accessGuard) {
        this.organizationRepository = organizationRepository;
        this.membershipRepository = membershipRepository;
        this.userRepository = userRepository;
        this.accessGuard = accessGuard;
    }

    @Transactional
    public OrganizationResponse create(UUID userId, CreateOrganizationRequest request) {
        User user = userRepository.findById(userId).orElseThrow(LawyerNotFoundException::new);
        if (user.getRole() != UserRole.LAWYER) {
            throw new PravoosException("Только юрист может создать организацию", HttpStatus.FORBIDDEN, "ORG_LAWYER_ONLY");
        }

        Organization organization = new Organization();
        organization.setName(request.name().trim());
        organization.setOwnerId(userId);
        Organization saved = organizationRepository.save(organization);

        OrganizationMembership membership = new OrganizationMembership();
        membership.setOrgId(saved.getId());
        membership.setUserId(userId);
        membership.setOrgRole(OrgRole.OWNER);
        membershipRepository.save(membership);

        log.info("Organization {} created by lawyer {}", saved.getId(), userId);
        return new OrganizationResponse(saved.getId(), saved.getName(), saved.getOwnerId(),
                OrgRole.OWNER, 1, saved.getCreatedAt());
    }

    @Transactional(readOnly = true)
    public List<OrganizationResponse> listMyOrganizations(UUID userId) {
        List<OrganizationMembership> memberships = membershipRepository.findByUserIdOrderByCreatedAtAsc(userId);
        if (memberships.isEmpty()) {
            return List.of();
        }
        Map<UUID, Organization> orgsById = organizationRepository.findAllById(
                        memberships.stream().map(OrganizationMembership::getOrgId).toList()).stream()
                .collect(Collectors.toMap(Organization::getId, Function.identity()));

        return memberships.stream()
                .map(membership -> {
                    Organization org = orgsById.get(membership.getOrgId());
                    if (org == null) {
                        return null;
                    }
                    long memberCount = membershipRepository.countByOrgId(org.getId());
                    return new OrganizationResponse(org.getId(), org.getName(), org.getOwnerId(),
                            membership.getOrgRole(), memberCount, org.getCreatedAt());
                })
                .filter(java.util.Objects::nonNull)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<OrganizationMemberResponse> listMembers(UUID userId, UUID orgId) {
        accessGuard.requireMember(orgId, userId);

        List<OrganizationMembership> memberships = membershipRepository.findByOrgIdOrderByCreatedAtAsc(orgId);
        List<UUID> memberIds = memberships.stream().map(OrganizationMembership::getUserId).toList();

        Map<UUID, User> usersById = userRepository.findByIdInWithProfile(memberIds).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));

        return memberships.stream()
                .map(membership -> toMemberResponse(membership, usersById.get(membership.getUserId())))
                .toList();
    }

    @Transactional
    public OrganizationMemberResponse changeMemberRole(UUID callerId, UUID orgId, UUID targetUserId, OrgRole newRole) {
        accessGuard.requireOwner(orgId, callerId);
        if (newRole == OrgRole.OWNER) {
            throw new OrganizationAccessDeniedException();
        }
        Organization organization = organizationRepository.findById(orgId)
                .orElseThrow(OrganizationNotFoundException::new);
        if (organization.getOwnerId().equals(targetUserId)) {
            throw new OrganizationAccessDeniedException();
        }
        OrganizationMembership target = membershipRepository.findByOrgIdAndUserId(orgId, targetUserId)
                .orElseThrow(NotOrganizationMemberException::new);
        target.setOrgRole(newRole);

        User user = userRepository.findById(targetUserId).orElse(null);
        log.info("Member {} role changed to {} in org {} by {}", targetUserId, newRole, orgId, callerId);
        return toMemberResponse(target, user);
    }

    @Transactional
    public void removeMember(UUID callerId, UUID orgId, UUID targetUserId) {
        OrganizationMembership callerMembership = accessGuard.requireManagerOrOwner(orgId, callerId);
        Organization organization = organizationRepository.findById(orgId)
                .orElseThrow(OrganizationNotFoundException::new);
        if (organization.getOwnerId().equals(targetUserId)) {
            throw new OrganizationAccessDeniedException();
        }
        OrganizationMembership target = membershipRepository.findByOrgIdAndUserId(orgId, targetUserId)
                .orElseThrow(NotOrganizationMemberException::new);
        if (callerMembership.getOrgRole() == OrgRole.MANAGER && target.getOrgRole() != OrgRole.MEMBER) {
            throw new OrganizationAccessDeniedException();
        }
        membershipRepository.delete(target);
        log.info("Member {} removed from org {} by {}", targetUserId, orgId, callerId);
    }

    @Transactional
    public void leave(UUID callerId, UUID orgId) {
        Organization organization = organizationRepository.findById(orgId)
                .orElseThrow(OrganizationNotFoundException::new);
        if (organization.getOwnerId().equals(callerId)) {
            throw new PravoosException("Владелец не может покинуть организацию", HttpStatus.CONFLICT, "ORG_OWNER_CANNOT_LEAVE");
        }
        OrganizationMembership membership = membershipRepository.findByOrgIdAndUserId(orgId, callerId)
                .orElseThrow(NotOrganizationMemberException::new);
        membershipRepository.delete(membership);
        log.info("User {} left org {}", callerId, orgId);
    }

    private OrganizationMemberResponse toMemberResponse(OrganizationMembership membership, User user) {
        String email = user != null ? user.getEmail() : null;
        String fullName = user != null && user.getLawyerProfile() != null
                ? user.getLawyerProfile().getFullName()
                : null;
        return new OrganizationMemberResponse(membership.getUserId(), email, fullName,
                membership.getOrgRole(), membership.getCreatedAt());
    }
}
