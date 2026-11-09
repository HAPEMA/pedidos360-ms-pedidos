package cl.duoc.pedidos.controller;

import cl.duoc.pedidos.config.CognitoAuthoritiesConverter;
import cl.duoc.pedidos.config.RabbitProperties;
import cl.duoc.pedidos.event.AdopcionConfirmadaEvent;
import cl.duoc.pedidos.event.AnimalAsignadoEvent;
import cl.duoc.pedidos.model.EstadoPedido;
import cl.duoc.pedidos.model.Pedido;
import cl.duoc.pedidos.repository.PedidoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.AmqpConnectException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** POST /api/pedidos/{id}/asignar y /confirmar: estados, dueño/admin y publicación de eventos. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
class PedidoAdopcionTest {

    private static final String READ = "pedidos360-api/read";
    private static final String WRITE = "pedidos360-api/write";

    @Autowired
    private MockMvc mvc;
    @Autowired
    private PedidoRepository repository;
    @Autowired
    private RabbitProperties props;

    @MockitoBean
    private RabbitTemplate rabbitTemplate;

    @BeforeEach
    void limpiar() {
        repository.deleteAll();
    }

    private static JwtRequestPostProcessor usuario(String username, String scopes, String... grupos) {
        return jwt()
                .jwt(j -> j.tokenValue("t").subject("sub-" + username).claim("username", username)
                        .claim("token_use", "access").claim("scope", scopes)
                        .claim("cognito:groups", List.of(grupos)))
                .authorities(new CognitoAuthoritiesConverter());
    }

    private Pedido pedido(EstadoPedido estado, String sub) {
        Pedido p = new Pedido();
        p.setEspecieId(7L);
        p.setEspecieNombre("Perro");
        p.setCantidad(1);
        p.setPrecioUnitario(1000);
        p.setTotal(1000);
        p.setEstado(estado);
        p.setCreadoPor("ana");
        p.setCreadoPorSub(sub);
        p.setCreadoEn(Instant.now());
        return repository.save(p);
    }

    // ---------- asignar ----------

    @Test
    void duenoAsignaYSePublicaAnimalAsignadoConSuSub() throws Exception {
        Pedido p = pedido(EstadoPedido.PENDIENTE, "sub-ana");

        mvc.perform(post("/api/pedidos/{id}/asignar", p.getId()).with(usuario("ana", WRITE)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("ASIGNADO"));

        assertThat(repository.findById(p.getId()).orElseThrow().getEstado()).isEqualTo(EstadoPedido.ASIGNADO);
        ArgumentCaptor<Object> evento = ArgumentCaptor.forClass(Object.class);
        verify(rabbitTemplate).convertAndSend(eq(props.exchanges().pedidos()),
                eq(props.routingKeys().animalAsignado()), evento.capture());
        AnimalAsignadoEvent e = (AnimalAsignadoEvent) evento.getValue();
        assertThat(e.pedidoId()).isEqualTo(p.getId());
        assertThat(e.especieId()).isEqualTo(7L);
        assertThat(e.usuarioSub()).isEqualTo("sub-ana");
    }

    @Test
    void adminAsignaPedidoAjenoYElEventoLlevaElSubDelDueno() throws Exception {
        Pedido p = pedido(EstadoPedido.PENDIENTE, "sub-ana");

        mvc.perform(post("/api/pedidos/{id}/asignar", p.getId()).with(usuario("root", WRITE, "ADMIN")))
                .andExpect(status().isOk());

        ArgumentCaptor<Object> evento = ArgumentCaptor.forClass(Object.class);
        verify(rabbitTemplate).convertAndSend(anyString(), eq(props.routingKeys().animalAsignado()), evento.capture());
        assertThat(((AnimalAsignadoEvent) evento.getValue()).usuarioSub()).isEqualTo("sub-ana");
    }

    @Test
    void otroUsuarioNoPuedeAsignar_403_ySinEvento() throws Exception {
        Pedido p = pedido(EstadoPedido.PENDIENTE, "sub-ana");

        mvc.perform(post("/api/pedidos/{id}/asignar", p.getId()).with(usuario("beto", WRITE)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("No eres el dueño de este pedido"));

        assertThat(repository.findById(p.getId()).orElseThrow().getEstado()).isEqualTo(EstadoPedido.PENDIENTE);
        verifyNoInteractions(rabbitTemplate);
    }

    @Test
    void asignarDosVeces_409_ConMensajeClaro() throws Exception {
        Pedido p = pedido(EstadoPedido.ASIGNADO, "sub-ana");

        mvc.perform(post("/api/pedidos/{id}/asignar", p.getId()).with(usuario("ana", WRITE)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.error", containsString("está en estado ASIGNADO")));
        verifyNoInteractions(rabbitTemplate);
    }

    @Test
    void asignarPedidoCancelado_409() throws Exception {
        Pedido p = pedido(EstadoPedido.CANCELADO, "sub-ana");

        mvc.perform(post("/api/pedidos/{id}/asignar", p.getId()).with(usuario("ana", WRITE)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error", containsString("CANCELADO")));
    }

    @Test
    void asignarPedidoInexistente_404() throws Exception {
        mvc.perform(post("/api/pedidos/{id}/asignar", 9999).with(usuario("ana", WRITE)))
                .andExpect(status().isNotFound());
    }

    @Test
    void asignarSinScopeWrite_403() throws Exception {
        Pedido p = pedido(EstadoPedido.PENDIENTE, "sub-ana");

        mvc.perform(post("/api/pedidos/{id}/asignar", p.getId()).with(usuario("ana", READ)))
                .andExpect(status().isForbidden());
        verifyNoInteractions(rabbitTemplate);
    }

    @Test
    void asignarSinTokenEs401() throws Exception {
        mvc.perform(post("/api/pedidos/1/asignar")).andExpect(status().isUnauthorized());
    }

    @Test
    void siRabbitFallaAlAsignarLaRespuestaSigueSiendo200() throws Exception {
        Pedido p = pedido(EstadoPedido.PENDIENTE, "sub-ana");
        doThrow(new AmqpConnectException(new RuntimeException("broker caído")))
                .when(rabbitTemplate).convertAndSend(anyString(), anyString(), any(Object.class));

        mvc.perform(post("/api/pedidos/{id}/asignar", p.getId()).with(usuario("ana", WRITE)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("ASIGNADO"));
        assertThat(repository.findById(p.getId()).orElseThrow().getEstado()).isEqualTo(EstadoPedido.ASIGNADO);
    }

    @Test
    void pedidoAnteriorSinSubSeReconocePorUsernameYSeCompletaElSub() throws Exception {
        Pedido p = pedido(EstadoPedido.PENDIENTE, null);

        mvc.perform(post("/api/pedidos/{id}/asignar", p.getId()).with(usuario("ana", WRITE)))
                .andExpect(status().isOk());

        assertThat(repository.findById(p.getId()).orElseThrow().getCreadoPorSub()).isEqualTo("sub-ana");
        verify(rabbitTemplate).convertAndSend(anyString(), eq(props.routingKeys().animalAsignado()), any(Object.class));
    }

    @Test
    void adminSobrePedidoAnteriorSinSubGuardaPeroNoPublica() throws Exception {
        Pedido p = pedido(EstadoPedido.PENDIENTE, null);

        mvc.perform(post("/api/pedidos/{id}/asignar", p.getId()).with(usuario("root", WRITE, "ADMIN")))
                .andExpect(status().isOk());

        verify(rabbitTemplate, never()).convertAndSend(anyString(), anyString(), any(Object.class));
    }

    // ---------- confirmar ----------

    @Test
    void duenoConfirmaYSePublicaAdopcionConfirmadaConElNombreDelAnimal() throws Exception {
        Pedido p = pedido(EstadoPedido.ASIGNADO, "sub-ana");

        mvc.perform(post("/api/pedidos/{id}/confirmar", p.getId()).with(usuario("ana", WRITE)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("CONFIRMADO"));

        ArgumentCaptor<Object> evento = ArgumentCaptor.forClass(Object.class);
        verify(rabbitTemplate).convertAndSend(eq(props.exchanges().pedidos()),
                eq(props.routingKeys().adopcionConfirmada()), evento.capture());
        AdopcionConfirmadaEvent e = (AdopcionConfirmadaEvent) evento.getValue();
        assertThat(e.pedidoId()).isEqualTo(p.getId());
        assertThat(e.animalNombre()).isEqualTo("Perro");
        assertThat(e.usuarioSub()).isEqualTo("sub-ana");
    }

    @Test
    void confirmarSinAsignar_409_ConMensajeClaro() throws Exception {
        Pedido p = pedido(EstadoPedido.PENDIENTE, "sub-ana");

        mvc.perform(post("/api/pedidos/{id}/confirmar", p.getId()).with(usuario("ana", WRITE)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error", containsString("primero hay que asignar el animal")));
        assertThat(repository.findById(p.getId()).orElseThrow().getEstado()).isEqualTo(EstadoPedido.PENDIENTE);
        verifyNoInteractions(rabbitTemplate);
    }

    @Test
    void confirmarDosVeces_409() throws Exception {
        Pedido p = pedido(EstadoPedido.CONFIRMADO, "sub-ana");

        mvc.perform(post("/api/pedidos/{id}/confirmar", p.getId()).with(usuario("ana", WRITE)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error", containsString("CONFIRMADO")));
        verifyNoInteractions(rabbitTemplate);
    }

    @Test
    void otroUsuarioNoPuedeConfirmar_403() throws Exception {
        Pedido p = pedido(EstadoPedido.ASIGNADO, "sub-ana");

        mvc.perform(post("/api/pedidos/{id}/confirmar", p.getId()).with(usuario("beto", WRITE)))
                .andExpect(status().isForbidden());
        verifyNoInteractions(rabbitTemplate);
    }

    @Test
    void adminConfirmaPedidoAjeno() throws Exception {
        Pedido p = pedido(EstadoPedido.ASIGNADO, "sub-ana");

        mvc.perform(post("/api/pedidos/{id}/confirmar", p.getId()).with(usuario("root", WRITE, "ADMIN")))
                .andExpect(status().isOk());
        verify(rabbitTemplate).convertAndSend(anyString(), eq(props.routingKeys().adopcionConfirmada()), any(Object.class));
    }

    @Test
    void siRabbitFallaAlConfirmarLaRespuestaSigueSiendo200() throws Exception {
        Pedido p = pedido(EstadoPedido.ASIGNADO, "sub-ana");
        doThrow(new AmqpConnectException(new RuntimeException("broker caído")))
                .when(rabbitTemplate).convertAndSend(anyString(), anyString(), any(Object.class));

        mvc.perform(post("/api/pedidos/{id}/confirmar", p.getId()).with(usuario("ana", WRITE)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("CONFIRMADO"));
    }
}
