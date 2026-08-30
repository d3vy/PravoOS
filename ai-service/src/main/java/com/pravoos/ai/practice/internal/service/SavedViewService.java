package com.pravoos.ai.practice.internal.service;

import com.pravoos.ai.practice.internal.dto.CreateSavedViewRequest;
import com.pravoos.ai.practice.internal.dto.SavedViewResponse;
import com.pravoos.ai.practice.internal.dto.UpdateSavedViewRequest;
import com.pravoos.ai.practice.internal.model.SavedViewScope;
import com.pravoos.ai.practice.internal.model.entity.SavedView;
import com.pravoos.ai.practice.internal.repository.jpa.SavedViewRepository;
import com.pravoos.ai.recyclebin.api.DeletionActor;
import com.pravoos.ai.recyclebin.api.RecycleBin;
import com.pravoos.ai.recyclebin.api.RecycleBinEntityType;
import com.pravoos.ai.shared.exception.OrganizationAccessException;
import com.pravoos.ai.shared.exception.SavedViewNameTakenException;
import com.pravoos.ai.shared.exception.SavedViewNotFoundException;
import com.pravoos.ai.shared.exception.SavedViewOrgRequiredException;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SavedViewService {

  private static final UUID NIL_ORG_SENTINEL = new UUID(0L, 0L);
  private static final int MAX_NAME_LENGTH = 80;

  private final SavedViewRepository savedViewRepository;
  private final RecycleBin recycleBin;
  private final SavedViewCatalogCache catalogCache;
  private final boolean catalogCacheEnabled;

  public SavedViewService(
      SavedViewRepository savedViewRepository,
      RecycleBin recycleBin,
      SavedViewCatalogCache catalogCache,
      @Value("${ai.catalog-cache.enabled:true}") boolean catalogCacheEnabled) {
    this.savedViewRepository = savedViewRepository;
    this.recycleBin = recycleBin;
    this.catalogCache = catalogCache;
    this.catalogCacheEnabled = catalogCacheEnabled;
  }

  @Transactional(readOnly = true)
  public List<SavedViewResponse> findVisible(
      SavedViewScope scope, UUID lawyerId, List<UUID> orgIds) {
    if (!catalogCacheEnabled) {
      return loadVisible(scope, lawyerId, orgIds);
    }
    String key = cacheKey(scope, lawyerId, orgIds);
    return catalogCache.get(key, k -> loadVisible(scope, lawyerId, orgIds));
  }

  private List<SavedViewResponse> loadVisible(
      SavedViewScope scope, UUID lawyerId, List<UUID> orgIds) {
    return savedViewRepository.findVisible(lawyerId, orgIdsOrSentinel(orgIds), scope).stream()
        .map(view -> SavedViewResponse.from(view, lawyerId))
        .toList();
  }

  private static String cacheKey(SavedViewScope scope, UUID lawyerId, List<UUID> orgIds) {
    String sortedOrgIds =
        orgIds == null
            ? ""
            : orgIds.stream().map(UUID::toString).sorted().collect(Collectors.joining(","));
    return scope + "|" + lawyerId + "|" + sortedOrgIds;
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
    SavedViewResponse response = SavedViewResponse.from(saveOrThrowNameTaken(view, name), lawyerId);
    catalogCache.evictAll();
    return response;
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
    SavedViewResponse response = SavedViewResponse.from(saveOrThrowNameTaken(view, name), lawyerId);
    catalogCache.evictAll();
    return response;
  }

  @Transactional
  public void delete(UUID viewId, DeletionActor actor) {
    requireOwnedView(viewId, actor.userId());
    recycleBin.moveToBin(RecycleBinEntityType.SAVED_VIEW, viewId.toString(), actor);
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
