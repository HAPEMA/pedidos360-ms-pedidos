package cl.duoc.pedidos.event;

import java.time.Instant;

/**
 * Una adopción quedó confirmada. Incluye el animal para que ms-documentos pueda emitir el ticket.
 * TODO: aún no existe la operación de confirmación; se publica cuando se implemente.
 */
public record AdopcionConfirmadaEvent(Long pedidoId, Long especieId, String animalNombre, String usuarioSub,
                                      Instant ocurridoEn) {
}
