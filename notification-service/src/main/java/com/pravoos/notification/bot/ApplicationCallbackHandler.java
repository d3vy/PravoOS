package com.pravoos.notification.bot;

import com.pravoos.notification.client.UserServiceClient;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;

import java.util.UUID;

@Component
public class ApplicationCallbackHandler {

    private static final String APPROVE_PREFIX = "approve:";
    private static final String REJECT_PREFIX = "reject:";

    private final UserServiceClient userServiceClient;

    public ApplicationCallbackHandler(UserServiceClient userServiceClient) {
        this.userServiceClient = userServiceClient;
    }

    public boolean supports(String callbackData) {
        return callbackData != null &&
                (callbackData.startsWith(APPROVE_PREFIX) || callbackData.startsWith(REJECT_PREFIX));
    }

    public String handle(String callbackData) {
        try {
            if (callbackData.startsWith(APPROVE_PREFIX)) {
                UUID id = UUID.fromString(callbackData.substring(APPROVE_PREFIX.length()));
                userServiceClient.approveApplication(id);
                return "Заявка принята";
            } else {
                UUID id = UUID.fromString(callbackData.substring(REJECT_PREFIX.length()));
                userServiceClient.rejectApplication(id);
                return "Заявка отклонена";
            }
        } catch (HttpClientErrorException.NotFound e) {
            return "Заявка не найдена";
        } catch (HttpClientErrorException.Conflict e) {
            return "Заявка уже обработана или email занят";
        } catch (HttpClientErrorException.UnprocessableEntity e) {
            return "Email не подтверждён — юрист должен перейти по ссылке в письме";
        } catch (HttpClientErrorException e) {
            throw e;
        }
    }
}
