package cl.duoc.pedidos.web;

public class CatalogoNoDisponibleException extends RuntimeException {
    public CatalogoNoDisponibleException(String mensaje, Throwable causa) {
        super(mensaje, causa);
    }
}
