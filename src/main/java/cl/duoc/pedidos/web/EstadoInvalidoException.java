package cl.duoc.pedidos.web;

/** La operación no corresponde al estado actual del pedido (→ 409). */
public class EstadoInvalidoException extends RuntimeException {
    public EstadoInvalidoException(String mensaje) {
        super(mensaje);
    }
}
