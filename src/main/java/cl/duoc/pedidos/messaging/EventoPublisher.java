package cl.duoc.pedidos.messaging;

import cl.duoc.pedidos.config.RabbitProperties;
import cl.duoc.pedidos.event.AdopcionConfirmadaEvent;
import cl.duoc.pedidos.event.AnimalAsignadoEvent;
import cl.duoc.pedidos.event.PedidoCreadoEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

/**
 * Publica eventos de dominio en RabbitMQ. Sin lógica de negocio.
 * Nunca lanza: si el broker falla se registra el error y la operación HTTP original sigue intacta.
 */
@Component
public class EventoPublisher {

    private static final Logger log = LoggerFactory.getLogger(EventoPublisher.class);

    private final RabbitTemplate rabbitTemplate;
    private final RabbitProperties props;

    public EventoPublisher(RabbitTemplate rabbitTemplate, RabbitProperties props) {
        this.rabbitTemplate = rabbitTemplate;
        this.props = props;
    }

    public void publicar(PedidoCreadoEvent evento) {
        enviar(props.routingKeys().pedidoCreado(), evento);
    }

    public void publicar(AnimalAsignadoEvent evento) {
        enviar(props.routingKeys().animalAsignado(), evento);
    }

    public void publicar(AdopcionConfirmadaEvent evento) {
        enviar(props.routingKeys().adopcionConfirmada(), evento);
    }

    private void enviar(String routingKey, Object evento) {
        try {
            rabbitTemplate.convertAndSend(props.exchanges().pedidos(), routingKey, evento);
        } catch (Exception e) {
            // Se traga a propósito: el dato ya está en la BD y el cliente no debe ver un 500 por la mensajería.
            log.error("No se pudo publicar {} con routing key '{}': {}",
                    evento.getClass().getSimpleName(), routingKey, e.toString());
        }
    }
}
