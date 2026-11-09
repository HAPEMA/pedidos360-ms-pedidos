package cl.duoc.pedidos.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Nombres de mensajería leídos de app.rabbit.* (application.yml). Sin strings sueltos en el código. */
@ConfigurationProperties(prefix = "app.rabbit")
public record RabbitProperties(Exchanges exchanges, Queues queues, String dlqSuffix, RoutingKeys routingKeys) {

    public record Exchanges(String pedidos, String dlx) {
    }

    public record Queues(String notificaciones) {
    }

    public record RoutingKeys(String pedidoCreado, String animalAsignado, String adopcionConfirmada) {
    }
}
