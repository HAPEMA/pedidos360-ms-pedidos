package cl.duoc.pedidos.config;

import cl.duoc.pedidos.web.ManejadorErrores;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authorization.AuthorityAuthorizationManager;
import org.springframework.security.authorization.AuthorizationManagers;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.util.List;

/**
 * Resource Server: este servicio valida el JWT por sí mismo aunque el API Gateway ya lo haya hecho.
 * Defense in Depth: si alguien llega directo al EC2 (saltándose el Gateway) igual necesita un token válido.
 */
@Configuration
public class SecurityConfig {

    static final String SCOPE_READ = "SCOPE_pedidos360-api/read";
    static final String SCOPE_WRITE = "SCOPE_pedidos360-api/write";

    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                // API stateless con Bearer token: no hay cookie de sesión que un sitio ajeno pueda
                // reutilizar, por eso CSRF no aplica y se deshabilita.
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .cors(Customizer.withDefaults())
                .authorizeHttpRequests(auth -> auth
                        // Sin esto, el forward interno a /error (p. ej. tras un 404) se evalúa como
                        // request anónima y el cliente recibiría un 403 engañoso.
                        .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                        // El preflight CORS del navegador nunca trae token.
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        // Health público para el health check del balanceador/monitoreo; no expone datos.
                        .requestMatchers("/actuator/health").permitAll()
                        // Mínimo privilegio: cada método exige el scope específico.
                        .requestMatchers(HttpMethod.GET, "/api/pedidos/**").hasAuthority(SCOPE_READ)
                        .requestMatchers(HttpMethod.POST, "/api/pedidos").hasAuthority(SCOPE_WRITE)
                        // Borrar exige las dos cosas: la app tiene permiso de escritura Y el usuario es ADMIN.
                        .requestMatchers(HttpMethod.DELETE, "/api/pedidos/**").access(AuthorizationManagers.allOf(
                                AuthorityAuthorizationManager.hasAuthority(SCOPE_WRITE),
                                AuthorityAuthorizationManager.hasRole("ADMIN")))
                        // Denegar por defecto: cualquier ruta no declarada queda cerrada.
                        .anyRequest().denyAll())
                .oauth2ResourceServer(oauth -> oauth
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter()))
                        .authenticationEntryPoint(entryPoint()))
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(entryPoint())
                        .accessDeniedHandler(accessDeniedHandler()));
        return http.build();
    }

    @Bean
    public JwtDecoder jwtDecoder(@Value("${app.security.issuer}") String issuer,
                                 @Value("${app.security.client-id}") String clientId) {
        // withJwkSetUri no contacta a Cognito al arrancar: las llaves se descargan con el primer token.
        // Así el servicio arranca aunque Cognito no sea alcanzable (y en los tests no se llama a internet).
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(issuer + "/.well-known/jwks.json").build();

        decoder.setJwtValidator(validadorCognito(issuer, clientId));
        return decoder;
    }

    static OAuth2TokenValidator<Jwt> validadorCognito(String issuer, String clientId) {
        return new DelegatingOAuth2TokenValidator<>(
                // Firma + exp/nbf + iss == nuestro User Pool (rechaza tokens de otros pools).
                JwtValidators.createDefaultWithIssuer(issuer),
                // Solo access tokens: un id token también está firmado por Cognito, pero no es para APIs.
                new JwtClaimValidator<String>("token_use", "access"::equals),
                // El access token de Cognito no trae "aud"; client_id cumple el papel de audience:
                // rechaza tokens emitidos para otra app cliente del mismo pool.
                new JwtClaimValidator<String>("client_id", clientId::equals));
    }

    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(new CognitoAuthoritiesConverter());
        // En Cognito "sub" es un UUID; "username" es el identificador legible que guardamos en creadoPor.
        converter.setPrincipalClaimName("username");
        return converter;
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource(@Value("${app.cors.origen}") String origen) {
        // Origen exacto (sin comodines): solo el front de Pedidos360 puede llamar desde el navegador.
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(List.of(origen));
        config.setAllowedMethods(List.of("GET", "POST", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type"));
        config.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    // 401 y 403 con el mismo formato JSON que el resto de errores (el front no tiene que parsear HTML).
    private AuthenticationEntryPoint entryPoint() {
        return (request, response, e) -> escribir(response, HttpStatus.UNAUTHORIZED,
                "Token ausente, inválido o expirado");
    }

    private AccessDeniedHandler accessDeniedHandler() {
        return (request, response, e) -> escribir(response, HttpStatus.FORBIDDEN,
                "No tienes permisos para esta operación");
    }

    private void escribir(HttpServletResponse response, HttpStatus status, String mensaje) throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        if (status == HttpStatus.UNAUTHORIZED) {
            // RFC 6750: indica al cliente que se espera un Bearer token.
            response.setHeader("WWW-Authenticate", "Bearer");
        }
        jsonMapper.writeValue(response.getOutputStream(), ManejadorErrores.cuerpo(status, mensaje));
    }
}
