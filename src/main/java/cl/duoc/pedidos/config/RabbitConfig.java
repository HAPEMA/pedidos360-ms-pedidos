package cl.duoc.pedidos.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Topología RabbitMQ de este servicio: solo declaraciones, sin lógica de negocio.
 * Nombres en app.rabbit.* (application.yml). Las declaraciones son idempotentes: el otro
 * servicio declara el mismo exchange con los mismos argumentos.
 */
@Configuration
@EnableConfigurationProperties(RabbitProperties.class)
public class RabbitConfig {

    private final RabbitProperties props;

    public RabbitConfig(RabbitProperties props) {
        this.props = props;
    }

    // --- Exchanges ---

    /** Eventos del pedido; el routing key decide qué colas lo reciben. */
    @Bean
    public TopicExchange pedidosExchange() {
        return new TopicExchange(props.exchanges().pedidos(), true, false);
    }

    /** Recibe los mensajes rechazados o vencidos de cualquier cola y los enruta a su DLQ. */
    @Bean
    public DirectExchange deadLetterExchange() {
        return new DirectExchange(props.exchanges().dlx(), true, false);
    }

    // --- Notificaciones: aviso asíncrono al crear un pedido ---

    @Bean
    public Queue notificacionesQueue() {
        return QueueBuilder.durable(props.queues().notificaciones())
                .withArgument("x-dead-letter-exchange", props.exchanges().dlx())
                .withArgument("x-dead-letter-routing-key", dlq(props.queues().notificaciones()))
                .build();
    }

    /** pedido.creado → cola de este servicio. */
    @Bean
    public Binding notificacionesBinding() {
        return BindingBuilder.bind(notificacionesQueue()).to(pedidosExchange()).with(props.routingKeys().pedidoCreado());
    }

    @Bean
    public Queue notificacionesDeadLetterQueue() {
        return QueueBuilder.durable(dlq(props.queues().notificaciones())).build();
    }

    /** Mensajes muertos de la cola → su DLQ, por routing key {cola}.dlq. */
    @Bean
    public Binding notificacionesDeadLetterBinding() {
        return BindingBuilder.bind(notificacionesDeadLetterQueue()).to(deadLetterExchange()).with(dlq(props.queues().notificaciones()));
    }

    // --- Serialización ---

    /** JSON (Jackson 3) en lugar de serialización Java; Spring Boot lo aplica a RabbitTemplate y listeners. */
    @Bean
    public MessageConverter jsonMessageConverter() {
        return new JacksonJsonMessageConverter();
    }

    private String dlq(String cola) {
        return cola + props.dlqSuffix();
    }
}
