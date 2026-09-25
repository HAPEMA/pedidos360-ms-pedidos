package cl.duoc.pedidos.config;

import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SecurityConfigTest {

    private static final String ISSUER = "https://cognito-idp.us-east-1.amazonaws.com/us-east-1_XXXXXXXXX";
    private static final String CLIENTE = "cliente-front";

    private final OAuth2TokenValidator<Jwt> validador = SecurityConfig.validadorCognito(ISSUER, CLIENTE);

    private Jwt.Builder token() {
        return Jwt.withTokenValue("t")
                .header("alg", "RS256")
                .issuer(ISSUER)
                .issuedAt(Instant.now().minusSeconds(60))
                .expiresAt(Instant.now().plusSeconds(3600))
                .claim("token_use", "access")
                .claim("client_id", CLIENTE)
                .claim("username", "ana")
                .claim("scope", "pedidos360-api/read pedidos360-api/write")
                .claim("cognito:groups", List.of("ADMIN"));
    }

    @Test
    void aceptaUnAccessTokenValido() {
        assertThat(validador.validate(token().build()).hasErrors()).isFalse();
    }

    @Test
    void rechazaUnIdToken() {
        assertThat(validador.validate(token().claim("token_use", "id").build()).hasErrors()).isTrue();
    }

    @Test
    void rechazaTokenDeOtraAppCliente() {
        assertThat(validador.validate(token().claim("client_id", "otra-app").build()).hasErrors()).isTrue();
    }

    @Test
    void rechazaTokenDeOtroIssuer() {
        assertThat(validador.validate(token().issuer("https://evil.example.com").build()).hasErrors()).isTrue();
    }

    @Test
    void rechazaTokenExpirado() {
        Jwt expirado = token().issuedAt(Instant.now().minusSeconds(7200))
                .expiresAt(Instant.now().minusSeconds(3600)).build();
        assertThat(validador.validate(expirado).hasErrors()).isTrue();
    }

    @Test
    void mapeaScopesGruposYUsername() {
        JwtAuthenticationConverter converter = new SecurityConfig().jwtAuthenticationConverter();
        var auth = converter.convert(token().build());

        assertThat(auth.getName()).isEqualTo("ana");
        assertThat(auth.getAuthorities()).extracting(GrantedAuthority::getAuthority)
                .contains("SCOPE_pedidos360-api/read", "SCOPE_pedidos360-api/write", "ROLE_ADMIN");
    }
}
