package cl.duoc.pedidos.messaging;

import cl.duoc.pedidos.event.AdopcionConfirmadaEvent;
import cl.duoc.pedidos.event.AnimalAsignadoEvent;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;

import java.nio.charset.StandardCharsets;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Fija el JSON que ven los consumidores (mismo conversor que RabbitConfig). Si cambia, hay que
 * actualizar las copias del contrato en ms-notificaciones y ms-documentos: sus tests usan estos mismos literales.
 */
class ContratoEventosTest {

    private final JacksonJsonMessageConverter conversor = new JacksonJsonMessageConverter();

    private String json(Object evento) {
        Message m = conversor.toMessage(evento, new MessageProperties());
        return new String(m.getBody(), StandardCharsets.UTF_8);
    }

    @Test
    void animalAsignadoSerializaConLosCamposQueEsperaNotificaciones() {
        String json = json(new AnimalAsignadoEvent(1L, 2L, "sub-1", Instant.parse("2026-11-09T10:00:00Z")));

        assertThat(json).isEqualTo(
                "{\"pedidoId\":1,\"especieId\":2,\"usuarioSub\":\"sub-1\",\"ocurridoEn\":\"2026-11-09T10:00:00Z\"}");
    }

    @Test
    void adopcionConfirmadaSerializaConLosCamposQueEsperaDocumentos() {
        String json = json(new AdopcionConfirmadaEvent(1L, 2L, "Perro", "sub-1",
                Instant.parse("2026-11-09T10:00:00Z")));

        assertThat(json).isEqualTo("{\"pedidoId\":1,\"especieId\":2,\"animalNombre\":\"Perro\","
                + "\"usuarioSub\":\"sub-1\",\"ocurridoEn\":\"2026-11-09T10:00:00Z\"}");
    }
}
