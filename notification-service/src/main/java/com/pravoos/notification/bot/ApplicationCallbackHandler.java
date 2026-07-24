package com.pravoos.notification.bot;

import com.pravoos.notification.client.UserServiceClient;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;

@Component
public class ApplicationCallbackHandler {

  private static final String APPROVE_FORCE_PREFIX = "approve_force:";
  private static final String APPROVE_PREFIX = "approve:";
  private static final String REJECT_PREFIX = "reject:";

  private final UserServiceClient userServiceClient;

  public ApplicationCallbackHandler(UserServiceClient userServiceClient) {
    this.userServiceClient = userServiceClient;
  }

  public boolean supports(String callbackData) {
    return callbackData != null
        && (callbackData.startsWith(APPROVE_FORCE_PREFIX)
            || callbackData.startsWith(APPROVE_PREFIX)
            || callbackData.startsWith(REJECT_PREFIX));
  }

  public ApplicationCallbackResult handle(String callbackData) {
    if (callbackData.startsWith(APPROVE_FORCE_PREFIX)) {
      return handleApproveForce(parseId(callbackData, APPROVE_FORCE_PREFIX));
    }
    if (callbackData.startsWith(APPROVE_PREFIX)) {
      return handleApprove(parseId(callbackData, APPROVE_PREFIX));
    }
    return handleReject(parseId(callbackData, REJECT_PREFIX));
  }

  private ApplicationCallbackResult handleApprove(UUID applicationId) {
    try {
      userServiceClient.approveApplication(applicationId);
      return ApplicationCallbackResult.terminal("Заявка принята");
    } catch (HttpClientErrorException.UnprocessableEntity e) {
      return ApplicationCallbackResult.emailNotVerified(applicationId);
    } catch (HttpClientErrorException.NotFound e) {
      return ApplicationCallbackResult.terminal("Заявка не найдена");
    } catch (HttpClientErrorException.Conflict e) {
      return ApplicationCallbackResult.terminal("Заявка уже обработана или email занят");
    }
  }

  private ApplicationCallbackResult handleApproveForce(UUID applicationId) {
    try {
      userServiceClient.approveApplicationForce(applicationId);
      return ApplicationCallbackResult.terminal("Заявка принята без подтверждения почты");
    } catch (HttpClientErrorException.NotFound e) {
      return ApplicationCallbackResult.terminal("Заявка не найдена");
    } catch (HttpClientErrorException.Conflict e) {
      return ApplicationCallbackResult.terminal("Заявка уже обработана или email занят");
    }
  }

  private ApplicationCallbackResult handleReject(UUID applicationId) {
    try {
      userServiceClient.rejectApplication(applicationId);
      return ApplicationCallbackResult.terminal("Заявка отклонена");
    } catch (HttpClientErrorException.NotFound e) {
      return ApplicationCallbackResult.terminal("Заявка не найдена");
    } catch (HttpClientErrorException.Conflict e) {
      return ApplicationCallbackResult.terminal("Заявка уже обработана");
    }
  }

  private UUID parseId(String callbackData, String prefix) {
    return UUID.fromString(callbackData.substring(prefix.length()));
  }
}
