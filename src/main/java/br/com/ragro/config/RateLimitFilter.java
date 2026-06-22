package br.com.ragro.config;

import br.com.ragro.controller.response.ErrorResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Rate limiting in-memory (token bucket via Bucket4j) para endpoints públicos de auth e endpoints
 * que geram custo externo (LLM da NVIDIA, Google Maps).
 *
 * <p>Registrado como filtro servlet APÓS a security chain (ver {@link RateLimitConfig}), então o
 * SecurityContext já está populado e a chave por usuário (sub do JWT) funciona; requests rejeitadas
 * pela security (401/403) nem chegam aqui e não consomem orçamento.
 *
 * <p>Os buckets vivem em memória por instância — suficiente para o deploy atual (1 task ECS). Com
 * múltiplas instâncias o limite passa a valer por instância (degradação aceitável) até este storage
 * ser trocado por um backend distribuído.
 */
public class RateLimitFilter extends OncePerRequestFilter {

  private static final String LIMIT_MESSAGE = "Muitas requisições. Tente novamente em instantes.";

  private final RateLimitProperties properties;
  private final ObjectMapper objectMapper;

  private final Cache<String, Bucket> buckets =
      Caffeine.newBuilder().expireAfterAccess(Duration.ofMinutes(10)).maximumSize(100_000).build();

  public RateLimitFilter(RateLimitProperties properties, ObjectMapper objectMapper) {
    this.properties = properties;
    this.objectMapper = objectMapper;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    if (!properties.isEnabled()) {
      filterChain.doFilter(request, response);
      return;
    }

    Rule rule = resolveRule(request);
    if (rule == null) {
      filterChain.doFilter(request, response);
      return;
    }

    String key = rule.name() + ":" + resolveKey(request, rule.perIp());
    Bucket bucket = buckets.get(key, k -> newBucket(rule.limitPerMinute()));
    ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);
    if (probe.isConsumed()) {
      filterChain.doFilter(request, response);
      return;
    }

    long retryAfterSeconds =
        Math.max(1, TimeUnit.NANOSECONDS.toSeconds(probe.getNanosToWaitForRefill()));
    response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
    response.setHeader("Retry-After", String.valueOf(retryAfterSeconds));
    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
    response.setCharacterEncoding("UTF-8");
    ErrorResponse body =
        ErrorResponse.builder()
            .timestamp(LocalDateTime.now())
            .status(HttpStatus.TOO_MANY_REQUESTS.value())
            .error(LIMIT_MESSAGE)
            .path(request.getRequestURI())
            .build();
    objectMapper.writeValue(response.getWriter(), body);
  }

  private record Rule(String name, int limitPerMinute, boolean perIp) {}

  private Rule resolveRule(HttpServletRequest request) {
    String path = request.getRequestURI();
    String method = request.getMethod();
    if ("POST".equals(method) && path.startsWith("/auth/register/")) {
      return new Rule("register", properties.getRegisterPerMinute(), true);
    }
    if ("POST".equals(method) && "/auth/password/forgot".equals(path)) {
      return new Rule("forgot", properties.getForgotPerMinute(), true);
    }
    if ("GET".equals(method) && "/recommendations".equals(path)) {
      return new Rule("recommendations", properties.getRecommendationsPerMinute(), false);
    }
    if ("/routes".equals(path) || path.startsWith("/routes/")) {
      return new Rule("routes", properties.getRoutesPerMinute(), false);
    }
    return null;
  }

  private String resolveKey(HttpServletRequest request, boolean perIp) {
    if (!perIp) {
      Authentication auth = SecurityContextHolder.getContext().getAuthentication();
      if (auth != null
          && auth.isAuthenticated()
          && !(auth instanceof AnonymousAuthenticationToken)) {
        return "user:" + auth.getName();
      }
    }
    return "ip:" + clientIp(request);
  }

  private String clientIp(HttpServletRequest request) {
    String forwarded = request.getHeader("X-Forwarded-For");
    if (forwarded != null && !forwarded.isBlank()) {
      // ÚLTIMO endereço da lista: é o único appendado pelo nosso proxy (ALB) e não forjável —
      // tudo à esquerda vem do cliente e usar o primeiro valor permitiria bypass por spoofing.
      // Premissa: exatamente 1 proxy confiável na frente (ALB); sem proxy, o header nem chega.
      String[] hops = forwarded.split(",");
      return hops[hops.length - 1].trim();
    }
    return request.getRemoteAddr();
  }

  private Bucket newBucket(int perMinute) {
    Bandwidth limit =
        Bandwidth.builder()
            .capacity(perMinute)
            .refillGreedy(perMinute, Duration.ofMinutes(1))
            .build();
    return Bucket.builder().addLimit(limit).build();
  }
}
