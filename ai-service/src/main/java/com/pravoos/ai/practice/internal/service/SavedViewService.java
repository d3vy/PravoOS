package com.pravoos.ai.practice.internal.service;

import com.pravoos.ai.practice.internal.dto.CreateSavedViewRequest;
import com.pravoos.ai.practice.internal.dto.SavedViewResponse;
import com.pravoos.ai.practice.internal.dto.UpdateSavedViewRequest;
import com.pravoos.ai.practice.internal.model.SavedViewScope;
import com.pravoos.ai.practice.internal.model.entity.SavedView;
import com.pravoos.ai.practice.internal.repository.jpa.SavedViewRepository;
import com.pravoos.ai.shared.exception.OrganizationAccessException;
import com.pravoos.ai.shared.exception.SavedViewNameTakenException;
import com.pravoos.ai.shared.exception.SavedViewNotFoundException;
import com.pravoos.ai.shared.exception.SavedViewOrgRequiredException;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SavedViewService {

  private static final UUID NIL_ORG_SENTINEL = new UUID(0L, 0L);
  private static final int MAX_NAME_LENGTH = 80;

  private final SavedViewRepository savedViewRepository;

  public SavedViewService(SavedViewRepository savedViewRepository) {
    this.savedViewRepository = savedViewRepository;
  }

  @Transactional(readOnly = true)
  public List<SavedViewResponse> findVisible(
      SavedViewScope scope, UUID lawyerId, List<UUID> orgIds) {
    return savedViewRepository.findVisible(lawyerId, orgIdsOrSentinel(orgIds), scope).stream()
        .map(view -> SavedViewResponse.from(view, lawyerId))
        .toList();
  }

  @Transactional
  public SavedViewResponse create(
      CreateSavedViewRequest request, UUID lawyerId, List<UUID> orgIds) {
    String name = truncateName(request.name());
    if (savedViewRepository.existsByLawyerIdAndScopeAndName(lawyerId, request.scope(), name)) {
      throw new SavedViewNameTakenException(name);
    }
    SavedView view = new SavedView();
    view.setLawyerId(lawyerId);
    view.setScope(request.scope());
    view.setName(name);
    view.setConfig(request.config());
    applySharing(view, request.sharedWithTeam(), request.orgId(), orgIds);
    return SavedViewResponse.from(saveOrThrowNameTaken(view, name), lawyerId);
  }

  @Transactional
  public SavedViewResponse update(
      UUID viewId, UpdateSavedViewRequest request, UUID lawyerId, List<UUID> orgIds) {
    SavedView view = requireOwnedView(viewId, lawyerId);
    String name = truncateName(request.name());
    if (!name.equals(view.getName())
        && savedViewRepository.existsByLawyerIdAndScopeAndName(lawyerId, view.getScope(), name)) {
      throw new SavedViewNameTakenException(name);
    }
    view.setName(name);
    view.setConfig(request.config());
    applySharing(view, request.sharedWithTeam(), request.orgId(), orgIds);
    return SavedViewResponse.from(saveOrThrowNameTaken(view, name), lawyerId);
  }

  @Transactional
  public void delete(UUID viewId, UUID lawyerId) {
    savedViewRepository.delete(requireOwnedView(viewId, lawyerId));
  }

  private SavedView saveOrThrowNameTaken(SavedView view, String name) {
    try {
      return savedViewRepository.save(view);
    } catch (DataIntegrityViolationException e) {
      throw new SavedViewNameTakenException(name);
    }
  }

  private void applySharing(SavedView view, boolean sharedWithTeam, UUID orgId, List<UUID> orgIds) {
    if (!sharedWithTeam) {
      view.setSharedWithTeam(false);
      view.setOrgId(null);
      return;
    }
    UUID targetOrgId = orgId != null ? orgId : singleOrgOrNull(orgIds);
    if (targetOrgId == null) {
      throw new SavedViewOrgRequiredException();
    }
    if (orgIds == null || !orgIds.contains(targetOrgId)) {
      throw new OrganizationAccessException(targetOrgId);
    }
    view.setSharedWithTeam(true);
    view.setOrgId(targetOrgId);
  }

  private String truncateName(String rawName) {
    String trimmed = rawName.trim();
    return trimmed.length() > MAX_NAME_LENGTH ? trimmed.substring(0, MAX_NAME_LENGTH) : trimmed;
  }

  private UUID singleOrgOrNull(List<UUID> orgIds) {
    return orgIds != null && orgIds.size() == 1 ? orgIds.get(0) : null;
  }

  private SavedView requireOwnedView(UUID viewId, UUID lawyerId) {
    return savedViewRepository
        .findByIdAndLawyerId(viewId, lawyerId)
        .orElseThrow(() -> new SavedViewNotFoundException(viewId));
  }

  private Collection<UUID> orgIdsOrSentinel(List<UUID> orgIds) {
    return (orgIds == null || orgIds.isEmpty()) ? List.of(NIL_ORG_SENTINEL) : orgIds;
  }
}
