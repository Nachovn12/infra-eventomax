package cl.duoc.eventomax.kafkaadmin.security;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.*;

public class EntraAuthoritiesConverter implements Converter<Jwt, Collection<GrantedAuthority>> {
    private static final Set<String> ROLES = Set.of("Admin", "Productor", "Organizador", "Auditor");

    @Override
    public Collection<GrantedAuthority> convert(Jwt jwt) {
        Set<GrantedAuthority> result = new HashSet<>();
        Object roles = jwt.getClaims().get("roles");
        if (roles instanceof Collection<?> values && values.stream().allMatch(String.class::isInstance)) {
            values.stream().map(String.class::cast).filter(ROLES::contains)
                    .forEach(role -> result.add(new SimpleGrantedAuthority("ROLE_" + role)));
        }

        Object scope = jwt.getClaims().get("scp");
        if (scope instanceof String scopes) {
            Arrays.stream(scopes.split(" ")).filter(s -> !s.isBlank())
                    .forEach(s -> result.add(new SimpleGrantedAuthority("SCOPE_" + s)));
        }
        return result;
    }
}

