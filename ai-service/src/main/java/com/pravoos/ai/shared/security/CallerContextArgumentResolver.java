package com.pravoos.ai.shared.security;

import jakarta.servlet.http.HttpServletRequest;
import java.security.Principal;
import org.springframework.core.MethodParameter;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

@Component
public class CallerContextArgumentResolver implements HandlerMethodArgumentResolver {

  @Override
  public boolean supportsParameter(MethodParameter parameter) {
    return CallerContext.class.equals(parameter.getParameterType());
  }

  @Override
  public CallerContext resolveArgument(
      MethodParameter parameter,
      ModelAndViewContainer modelAndViewContainer,
      NativeWebRequest webRequest,
      WebDataBinderFactory binderFactory) {
    return new CallerContext(authentication(webRequest));
  }

  private Authentication authentication(NativeWebRequest webRequest) {
    HttpServletRequest request = webRequest.getNativeRequest(HttpServletRequest.class);
    Principal principal = request == null ? null : request.getUserPrincipal();
    if (principal instanceof Authentication requestAuthentication) {
      return requestAuthentication;
    }
    return SecurityContextHolder.getContext().getAuthentication();
  }
}
