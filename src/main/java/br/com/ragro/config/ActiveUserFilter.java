package br.com.ragro.config;

import br.com.ragro.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class ActiveUserFilter extends OncePerRequestFilter {

  private final UserRepository userRepository;
  private final ObjectMapper objectMapper;

  public ActiveUserFilter(UserRepository userRepository) {
    this.userRepository = userRepository;
    this.objectMapper = new ObjectMapper();
    this.objectMapper.registerModule(new JavaTimeModule());
    this.objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {

    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

    if (!(authentication instanceof JwtAuthenticationToken jwtAuth)) {
      filterChain.doFilter(request, response);
      return;
    }

    String sub = jwtAuth.getToken().getClaimAsString("sub");

    var userOpt = userRepository.findByAuthSub(sub);

    if (userOpt.isEmpty() || !userOpt.get().isActive()) {
      writeErrorResponse(response, request.getRequestURI());
      return;
    }

    request.setAttribute("authenticatedUser", userOpt.get());
    filterChain.doFilter(request, response);
  }

  private void writeErrorResponse(HttpServletResponse response, String path) throws IOException {
    response.setStatus(HttpServletResponse.SC_FORBIDDEN);
    response.setContentType("application/json");

    Map<String, Object> body = new LinkedHashMap<>();
    body.put("timestamp", LocalDateTime.now().toString());
    body.put("status", HttpServletResponse.SC_FORBIDDEN);
    body.put("error", "Conta desativada");
    body.put("path", path);

    objectMapper.writeValue(response.getOutputStream(), body);
  }
}
