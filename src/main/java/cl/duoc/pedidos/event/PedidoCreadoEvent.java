package cl.duoc.pedidos.event;

import cl.duoc.pedidos.dto.PedidoResponse;

import java.time.Instant;

/** Un pedido ya quedó guardado. usuarioSub = claim "sub" del JWT (UUID estable de Cognito). */
public record PedidoCreadoEvent(Long pedidoId, Long especieId, String especieNombre, Integer cantidad,
                                Integer total, String usuarioSub, Instant ocurridoEn) {

    public static PedidoCreadoEvent de(PedidoResponse pedido, String usuarioSub) {
        return new PedidoCreadoEvent(pedido.id(), pedido.especieId(), pedido.especieNombre(),
                pedido.cantidad(), pedido.total(), usuarioSub, Instant.now());
    }
}
