package cl.duoc.eventomax.kafkaadmin.security;

import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.util.Assert;

import java.time.Instant;

public final class JwtValidation {
    private JwtValidation() {}

    public static OAuth2TokenValidator<Jwt> validator(String issuer, String audience) {
        Assert.hasText(issuer, "ENTRA_ISSUER_URI is required");
        Assert.hasText(audience, "ENTRA_AUDIENCE is required");

        OAuth2TokenValidator<Jwt> required = jwt -> {
            Object aud = jwt.getClaims().get("aud");
            boolean matches = aud instanceof java.util.Collection<?> values
                    && values.stream().allMatch(String.class::isInstance) && values.contains(audience);
            if (!matches || !(jwt.getClaims().get("exp") instanceof Instant)) {
                return OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", "Required claims are invalid", null));
            }
            return OAuth2TokenValidatorResult.success();
        };

        return new DelegatingOAuth2TokenValidator<>(JwtValidators.createDefaultWithIssuer(issuer), required);
    }
}

