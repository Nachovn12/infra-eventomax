package cl.duoc.eventomax.rabbitadmin;
import cl.duoc.eventomax.rabbitadmin.security.*;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import java.time.Instant;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
class JwtValidationTest {
    private Jwt.Builder token() { return Jwt.withTokenValue("test").header("alg","RS256").issuer("https://issuer.invalid").audience(List.of("api")).expiresAt(Instant.now().plusSeconds(120)); }
    @Test void validClaims() { assertThat(JwtValidation.validator("https://issuer.invalid","api").validate(token().build()).hasErrors()).isFalse(); }
    @Test void wrongAudience() { assertThat(JwtValidation.validator("https://issuer.invalid","other").validate(token().build()).hasErrors()).isTrue(); }
    @Test void wrongIssuer() { assertThat(JwtValidation.validator("https://other.invalid","api").validate(token().build()).hasErrors()).isTrue(); }
    @Test void expired() { assertThat(JwtValidation.validator("https://issuer.invalid","api").validate(token().expiresAt(Instant.now().minusSeconds(120)).build()).hasErrors()).isTrue(); }
    @Test void expiryRequired() { var jwt=Jwt.withTokenValue("test").header("alg","RS256").issuer("https://issuer.invalid").audience(List.of("api")).build(); assertThat(JwtValidation.validator("https://issuer.invalid","api").validate(jwt).hasErrors()).isTrue(); }
    @Test void malformedRolesGrantNoAdmin() { var authorities = new EntraAuthoritiesConverter().convert(token().claim("roles",List.of("Admin",3)).claim("scp","access_as_user").build()); assertThat(authorities).extracting("authority").containsExactly("SCOPE_access_as_user"); }
    @Test void scopeArrayDoesNotGrantDelegatedPermission() { var authorities = new EntraAuthoritiesConverter().convert(token().claim("roles",List.of("Admin")).claim("scp",List.of("access_as_user")).build()); assertThat(authorities).extracting("authority").containsExactly("ROLE_Admin"); }
}
