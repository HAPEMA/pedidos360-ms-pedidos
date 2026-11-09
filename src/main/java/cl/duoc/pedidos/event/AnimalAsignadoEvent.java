package cl.duoc.pedidos.event;

import java.time.Instant;

/**
 * Un animal fue asignado a un pedido.
 * Se publica en POST /api/pedidos/{id}/asignar, después de guardar. usuarioSub = sub del dueño del pedido.
 */
public record AnimalAsignadoEvent(Long pedidoId, Long especieId, String usuarioSub, Instant ocurridoEn) {
}
