package cl.duoc.eventomax.kafkaadmin.security;

import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.util.Assert;

public final class JwtValidation {
    private JwtValidation() {}

    public static OAuth2TokenValidator<Jwt> validator(String issuer, String audience) {
        Assert.hasText(issuer, "ENTRA_ISSUER_URI is required");
        Assert.hasText(audience, "ENTRA_AUDIENCE is required");

        OAuth2TokenValidator<Jwt> required = jwt -> {
            java.util.List<String> audiences = jwt.getAudience();
            boolean matches = audiences != null && audiences.contains(audience);
            if (!matches || jwt.getExpiresAt() == null) {
                return OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", "Required claims are invalid", null));
            }
            return OAuth2TokenValidatorResult.success();
        };

        return new DelegatingOAuth2TokenValidator<>(JwtValidators.createDefaultWithIssuer(issuer), required);
    }
}

