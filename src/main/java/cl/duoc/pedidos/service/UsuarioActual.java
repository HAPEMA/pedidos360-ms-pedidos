package cl.duoc.pedidos.service;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * Identidad del usuario autenticado, construida solo a partir del JWT ya validado.
 */
public record UsuarioActual(String username, boolean esAdmin, String sub) {

    public UsuarioActual(String username, boolean esAdmin) {
        this(username, esAdmin, null);
    }

    public static final String ROL_ADMIN = "ROLE_ADMIN";

    public static UsuarioActual desde(Authentication auth) {
        String username = auth.getName();
        String sub = null;
        // Se lee el claim "username" directamente para no depender de cómo se armó el token de autenticación.
        if (auth.getPrincipal() instanceof Jwt jwt && jwt.getClaimAsString("username") != null) {
            username = jwt.getClaimAsString("username");
        }
        if (auth.getPrincipal() instanceof Jwt jwt) {
            sub = jwt.getSubject();
        }
        boolean esAdmin = auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(ROL_ADMIN::equals);
        return new UsuarioActual(username, esAdmin, sub);
    }
}
