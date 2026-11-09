package cl.duoc.pedidos.event;

import java.time.Instant;

/**
 * Una adopción quedó confirmada. Incluye el animal para que ms-documentos pueda emitir el ticket.
 * Se publica en POST /api/pedidos/{id}/confirmar, después de guardar. usuarioSub = sub del dueño del pedido.
 */
public record AdopcionConfirmadaEvent(Long pedidoId, Long especieId, String animalNombre, String usuarioSub,
                                      Instant ocurridoEn) {
}
