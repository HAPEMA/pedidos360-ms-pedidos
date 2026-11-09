package cl.duoc.pedidos.model;

public enum EstadoPedido {
    PENDIENTE,
    /** El animal ya fue asignado al dueño del pedido; falta confirmar la adopción. */
    ASIGNADO,
    CONFIRMADO,
    CANCELADO
}
