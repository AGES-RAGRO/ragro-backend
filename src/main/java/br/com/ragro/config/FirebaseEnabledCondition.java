package br.com.ragro.config;

import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;

/**
 * Matches only when {@code firebase.service-account-json} is present and non-blank. Reads the
 * resolved property instead of inlining the (large, secret) JSON into a SpEL expression.
 */
public class FirebaseEnabledCondition implements Condition {

  @Override
  public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
    String json = context.getEnvironment().getProperty("firebase.service-account-json", "");
    return json != null && !json.isBlank();
  }
}
