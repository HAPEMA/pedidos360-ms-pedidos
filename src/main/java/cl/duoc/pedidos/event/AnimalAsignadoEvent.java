package cl.duoc.pedidos.event;

import java.time.Instant;

/**
 * Un animal fue asignado a un pedido.
 * TODO: aún no existe la operación de asignación; se publica cuando se implemente.
 */
public record AnimalAsignadoEvent(Long pedidoId, Long especieId, String usuarioSub, Instant ocurridoEn) {
}
