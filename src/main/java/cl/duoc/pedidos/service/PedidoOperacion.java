package cl.duoc.pedidos.service;

import cl.duoc.pedidos.dto.PedidoResponse;

/** Resultado de asignar/confirmar: el pedido guardado y el sub de su dueño (destino del evento). */
public record PedidoOperacion(PedidoResponse pedido, String usuarioSub) {
}
