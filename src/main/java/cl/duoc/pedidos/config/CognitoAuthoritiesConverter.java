package cl.duoc.pedidos.config;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Traduce el access token de Cognito a authorities de Spring:
 *  - claim "scope"          → SCOPE_xxx (qué puede hacer la app cliente: read / write)
 *  - claim "cognito:groups" → ROLE_xxx  (quién es el usuario: ADMIN, CLIENTE)
 * Separar scopes y roles permite exigir ambos a la vez (p. ej. DELETE).
 */
public class CognitoAuthoritiesConverter implements Converter<Jwt, Collection<GrantedAuthority>> {

    private final JwtGrantedAuthoritiesConverter scopes = new JwtGrantedAuthoritiesConverter();

    @Override
    public Collection<GrantedAuthority> convert(Jwt jwt) {
        List<GrantedAuthority> authorities = new ArrayList<>(scopes.convert(jwt));
        List<String> grupos = jwt.getClaimAsStringList("cognito:groups");
        if (grupos != null) {
            grupos.forEach(g -> authorities.add(new SimpleGrantedAuthority("ROLE_" + g)));
        }
        return authorities;
    }
}
