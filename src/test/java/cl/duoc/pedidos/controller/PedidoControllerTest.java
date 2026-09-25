package cl.duoc.pedidos.controller;

import cl.duoc.pedidos.client.CatalogoClient;
import cl.duoc.pedidos.config.CognitoAuthoritiesConverter;
import cl.duoc.pedidos.dto.EspecieDto;
import cl.duoc.pedidos.model.EstadoPedido;
import cl.duoc.pedidos.model.Pedido;
import cl.duoc.pedidos.repository.PedidoRepository;
import cl.duoc.pedidos.web.CatalogoNoDisponibleException;
import cl.duoc.pedidos.web.RecursoNoEncontradoException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
class PedidoControllerTest {

    private static final String READ = "pedidos360-api/read";
    private static final String WRITE = "pedidos360-api/write";
    private static final String TOKEN = "token-de-prueba";
    private static final String BEARER = "Bearer " + TOKEN;
    private static final String BODY_VALIDO = "{\"especieId\":1,\"cantidad\":2}";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private PedidoRepository repository;

    // Ningún test llama al ms-catalogo real.
    @MockitoBean
    private CatalogoClient catalogoClient;

    @BeforeEach
    void limpiar() {
        repository.deleteAll();
    }

    /** JWT simulado con los mismos claims que emite Cognito, convertido con el converter real. */
    private static JwtRequestPostProcessor usuario(String username, String scopes, String... grupos) {
        return jwt()
                .jwt(j -> j.tokenValue(TOKEN)
                        .subject("uuid-" + username)
                        .claim("username", username)
                        .claim("token_use", "access")
                        .claim("scope", scopes)
                        .claim("cognito:groups", List.of(grupos)))
                .authorities(new CognitoAuthoritiesConverter());
    }

    private Pedido guardarPedido(String creadoPor) {
        Pedido p = new Pedido();
        p.setEspecieId(1L);
        p.setEspecieNombre("Guepardo");
        p.setCantidad(1);
        p.setPrecioUnitario(2500000);
        p.setTotal(2500000);
        p.setEstado(EstadoPedido.PENDIENTE);
        p.setCreadoPor(creadoPor);
        p.setCreadoEn(Instant.now());
        return repository.save(p);
    }

    // ---------- 401 / 403 de la cadena de seguridad ----------

    @Test
    void sinTokenResponde401ConJson() throws Exception {
        mvc.perform(get("/api/pedidos"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.error").exists());
    }

    @Test
    void tokenMalformadoResponde401() throws Exception {
        // Sin jwt(): pasa por el JwtDecoder real, que rechaza el token antes de buscar llaves en Cognito.
        mvc.perform(get("/api/pedidos").header(HttpHeaders.AUTHORIZATION, "Bearer no-es-un-jwt"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    void postSinScopeWriteResponde403ConJson() throws Exception {
        mvc.perform(post("/api/pedidos").with(usuario("ana", READ, "CLIENTE"))
                        .contentType(MediaType.APPLICATION_JSON).content(BODY_VALIDO))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));
        verify(catalogoClient, never()).obtenerEspecie(anyLong(), anyString());
    }

    @Test
    void getSinScopeReadResponde403() throws Exception {
        mvc.perform(get("/api/pedidos").with(usuario("ana", WRITE, "CLIENTE")))
                .andExpect(status().isForbidden());
    }

    @Test
    void healthEsPublico() throws Exception {
        mvc.perform(get("/actuator/health")).andExpect(status().isOk());
    }

    @Test
    void rutaNoDeclaradaQuedaDenegada() throws Exception {
        mvc.perform(get("/actuator/env").with(usuario("ana", READ + " " + WRITE, "ADMIN")))
                .andExpect(status().isForbidden());
    }

    @Test
    void preflightCorsDelFrontSeAcepta() throws Exception {
        mvc.perform(options("/api/pedidos")
                        .header(HttpHeaders.ORIGIN, "http://localhost:4200")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "Authorization, Content-Type"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:4200"));
    }

    @Test
    void preflightCorsDeOtroOrigenSeRechaza() throws Exception {
        mvc.perform(options("/api/pedidos")
                        .header(HttpHeaders.ORIGIN, "https://sitio-malicioso.example")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST"))
                .andExpect(status().isForbidden());
    }

    // ---------- POST ----------

    @Test
    void postValidoResponde201ConCreadoPorDelJwtYReenviaElToken() throws Exception {
        given(catalogoClient.obtenerEspecie(1L, BEARER)).willReturn(new EspecieDto(1L, "Guepardo", 2500000, 3));

        mvc.perform(post("/api/pedidos").with(usuario("ana", READ + " " + WRITE, "CLIENTE"))
                        .contentType(MediaType.APPLICATION_JSON)
                        // Campos extra (precio, creadoPor) se ignoran: nunca se confía en el body.
                        .content("{\"especieId\":1,\"cantidad\":2,\"precioUnitario\":1,\"creadoPor\":\"otro\"}"))
                .andExpect(status().isCreated())
                .andExpect(header().exists(HttpHeaders.LOCATION))
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.especieNombre").value("Guepardo"))
                .andExpect(jsonPath("$.cantidad").value(2))
                .andExpect(jsonPath("$.precioUnitario").value(2500000))
                .andExpect(jsonPath("$.total").value(5000000))
                .andExpect(jsonPath("$.estado").value("PENDIENTE"))
                .andExpect(jsonPath("$.creadoPor").value("ana"))
                .andExpect(jsonPath("$.creadoEn").isString());

        verify(catalogoClient).obtenerEspecie(eq(1L), eq(BEARER));
        assertThat(repository.findAll()).singleElement()
                .satisfies(p -> assertThat(p.getCreadoPor()).isEqualTo("ana"));
    }

    @Test
    void stockInsuficienteResponde409() throws Exception {
        given(catalogoClient.obtenerEspecie(1L, BEARER)).willReturn(new EspecieDto(1L, "Guepardo", 2500000, 1));

        mvc.perform(post("/api/pedidos").with(usuario("ana", WRITE, "CLIENTE"))
                        .contentType(MediaType.APPLICATION_JSON).content(BODY_VALIDO))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.error").value("Solo quedan 1 ejemplares de Guepardo"));
        assertThat(repository.count()).isZero();
    }

    @Test
    void especieInexistenteResponde404() throws Exception {
        given(catalogoClient.obtenerEspecie(99L, BEARER))
                .willThrow(new RecursoNoEncontradoException("La especie 99 no existe en el catálogo"));

        mvc.perform(post("/api/pedidos").with(usuario("ana", WRITE, "CLIENTE"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"especieId\":99,\"cantidad\":1}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void catalogoCaidoResponde502() throws Exception {
        given(catalogoClient.obtenerEspecie(1L, BEARER))
                .willThrow(new CatalogoNoDisponibleException("ms-catalogo no está disponible", null));

        mvc.perform(post("/api/pedidos").with(usuario("ana", WRITE, "CLIENTE"))
                        .contentType(MediaType.APPLICATION_JSON).content(BODY_VALIDO))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.status").value(502));
    }

    @Test
    void bodyInvalidoResponde400ConDetalles() throws Exception {
        mvc.perform(post("/api/pedidos").with(usuario("ana", WRITE, "CLIENTE"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"cantidad\":9}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.detalles.especieId").exists())
                .andExpect(jsonPath("$.detalles.cantidad").exists());
        verify(catalogoClient, never()).obtenerEspecie(anyLong(), anyString());
    }

    @Test
    void jsonMalformadoResponde400() throws Exception {
        mvc.perform(post("/api/pedidos").with(usuario("ana", WRITE, "CLIENTE"))
                        .contentType(MediaType.APPLICATION_JSON).content("{no es json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    // ---------- GET ----------

    @Test
    void usuarioNormalSoloVeSusPedidos() throws Exception {
        guardarPedido("ana");
        guardarPedido("ana");
        guardarPedido("beto");

        mvc.perform(get("/api/pedidos").with(usuario("ana", READ, "CLIENTE")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].creadoPor").value("ana"))
                .andExpect(jsonPath("$[1].creadoPor").value("ana"));
    }

    @Test
    void adminVeTodosLosPedidosDelMasRecienteAlMasAntiguo() throws Exception {
        Pedido antiguo = guardarPedido("ana");
        Pedido reciente = guardarPedido("beto");

        mvc.perform(get("/api/pedidos").with(usuario("admin", READ, "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].id").value(reciente.getId()))
                .andExpect(jsonPath("$[1].id").value(antiguo.getId()));
    }

    @Test
    void getDePedidoPropioResponde200() throws Exception {
        Pedido propio = guardarPedido("ana");

        mvc.perform(get("/api/pedidos/{id}", propio.getId()).with(usuario("ana", READ, "CLIENTE")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(propio.getId()));
    }

    @Test
    void getDePedidoAjenoResponde403ConJson() throws Exception {
        Pedido ajeno = guardarPedido("beto");

        mvc.perform(get("/api/pedidos/{id}", ajeno.getId()).with(usuario("ana", READ, "CLIENTE")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.error").exists());
    }

    @Test
    void adminPuedeVerPedidoAjeno() throws Exception {
        Pedido ajeno = guardarPedido("beto");

        mvc.perform(get("/api/pedidos/{id}", ajeno.getId()).with(usuario("admin", READ, "ADMIN")))
                .andExpect(status().isOk());
    }

    @Test
    void idInexistenteResponde404ConJson() throws Exception {
        mvc.perform(get("/api/pedidos/{id}", 9999).with(usuario("ana", READ, "CLIENTE")))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").exists());
    }

    // ---------- DELETE ----------

    @Test
    void deleteComoClienteResponde403() throws Exception {
        Pedido pedido = guardarPedido("ana");

        mvc.perform(delete("/api/pedidos/{id}", pedido.getId()).with(usuario("ana", READ + " " + WRITE, "CLIENTE")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));
        assertThat(repository.existsById(pedido.getId())).isTrue();
    }

    @Test
    void deleteComoAdminSinScopeWriteResponde403() throws Exception {
        Pedido pedido = guardarPedido("ana");

        mvc.perform(delete("/api/pedidos/{id}", pedido.getId()).with(usuario("admin", READ, "ADMIN")))
                .andExpect(status().isForbidden());
    }

    @Test
    void deleteComoAdminResponde204() throws Exception {
        Pedido pedido = guardarPedido("ana");

        mvc.perform(delete("/api/pedidos/{id}", pedido.getId()).with(usuario("admin", READ + " " + WRITE, "ADMIN")))
                .andExpect(status().isNoContent());
        assertThat(repository.existsById(pedido.getId())).isFalse();
    }

    @Test
    void deleteDeIdInexistenteResponde404() throws Exception {
        mvc.perform(delete("/api/pedidos/{id}", 9999).with(usuario("admin", WRITE, "ADMIN")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }
}
