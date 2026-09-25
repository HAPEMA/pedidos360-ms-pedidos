package cl.duoc.pedidos.dto;

import cl.duoc.pedidos.model.EstadoPedido;
import cl.duoc.pedidos.model.Pedido;

import java.time.Instant;

public record PedidoResponse(
        Long id,
        Long especieId,
        String especieNombre,
        Integer cantidad,
        Integer precioUnitario,
        Integer total,
        EstadoPedido estado,
        String creadoPor,
        Instant creadoEn) {

    public static PedidoResponse desde(Pedido p) {
        return new PedidoResponse(p.getId(), p.getEspecieId(), p.getEspecieNombre(), p.getCantidad(),
                p.getPrecioUnitario(), p.getTotal(), p.getEstado(), p.getCreadoPor(), p.getCreadoEn());
    }
}
