package cl.duoc.pedidos.event;

import java.time.Instant;

/**
 * Una adopción quedó confirmada.
 * TODO: aún no existe la operación de confirmación; se publica cuando se implemente.
 */
public record AdopcionConfirmadaEvent(Long pedidoId, String usuarioSub, Instant ocurridoEn) {
}
