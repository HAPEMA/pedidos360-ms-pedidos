package cl.duoc.pedidos.controller;

import cl.duoc.pedidos.client.CatalogoClient;
import cl.duoc.pedidos.config.CognitoAuthoritiesConverter;
import cl.duoc.pedidos.config.RabbitProperties;
import cl.duoc.pedidos.dto.EspecieDto;
import cl.duoc.pedidos.event.PedidoCreadoEvent;
import cl.duoc.pedidos.repository.PedidoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.AmqpConnectException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
class PedidoEventosTest {

    @Autowired
    private MockMvc mvc;
    @Autowired
    private PedidoRepository repository;
    @Autowired
    private RabbitProperties props;

    @MockitoBean
    private CatalogoClient catalogoClient;
    @MockitoBean
    private RabbitTemplate rabbitTemplate;

    @BeforeEach
    void preparar() {
        repository.deleteAll();
        given(catalogoClient.obtenerEspecie(any(), anyString()))
                .willReturn(new EspecieDto(1L, "Perro", 1000, 10));
    }

    private org.springframework.test.web.servlet.request.RequestPostProcessor usuario() {
        return jwt().jwt(j -> j.tokenValue("t").subject("sub-123").claim("username", "ana")
                        .claim("token_use", "access").claim("scope", "pedidos360-api/write"))
                .authorities(new CognitoAuthoritiesConverter());
    }

    @Test
    void crearPublicaPedidoCreadoConElSubDelJwtDespuesDeGuardar() throws Exception {
        mvc.perform(post("/api/pedidos").with(usuario()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"especieId\":1,\"cantidad\":2}"))
                .andExpect(status().isCreated());

        ArgumentCaptor<Object> evento = ArgumentCaptor.forClass(Object.class);
        verify(rabbitTemplate).convertAndSend(eq(props.exchanges().pedidos()),
                eq(props.routingKeys().pedidoCreado()), evento.capture());
        PedidoCreadoEvent e = (PedidoCreadoEvent) evento.getValue();
        assertThat(e.usuarioSub()).isEqualTo("sub-123");
        assertThat(repository.findById(e.pedidoId()).orElseThrow().getCreadoPorSub()).isEqualTo("sub-123");
    }

    @Test
    void siRabbitFallaLaRespuestaHttpSigueSiendo201() throws Exception {
        doThrow(new AmqpConnectException(new RuntimeException("broker caído")))
                .when(rabbitTemplate).convertAndSend(anyString(), anyString(), any(Object.class));

        mvc.perform(post("/api/pedidos").with(usuario()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"especieId\":1,\"cantidad\":2}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists());
        assertThat(repository.findAll()).hasSize(1);
    }
}
