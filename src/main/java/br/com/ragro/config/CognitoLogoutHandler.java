package br.com.ragro.config;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.logout.SimpleUrlLogoutSuccessHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Component
public class CognitoLogoutHandler extends SimpleUrlLogoutSuccessHandler {

    @Value("${aws.cognito.domain}")
    private String cognitoDomain;

    @Value("${AWS_COGNITO_CLIENT_ID}")
    private String clientId;

    @Value("${aws.cognito.logout-uri}")
    private String logoutUri;

    @Override
    public void onLogoutSuccess(
            HttpServletRequest request,
            HttpServletResponse response,
            Authentication authentication
    ) throws IOException, ServletException {
        String normalizedDomain = cognitoDomain.endsWith("/")
            ? cognitoDomain.substring(0, cognitoDomain.length() - 1)
            : cognitoDomain;

        String logoutUrl = UriComponentsBuilder
            .fromHttpUrl(normalizedDomain)
            .path("/logout")
                .queryParam("client_id", clientId)
                .queryParam("logout_uri", logoutUri)
                .encode(StandardCharsets.UTF_8)
                .toUriString();

        response.sendRedirect(logoutUrl);
    }
}

