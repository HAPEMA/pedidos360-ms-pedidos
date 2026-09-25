package cl.duoc.pedidos.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

// Solo especie y cantidad: precio, stock y usuario nunca se aceptan desde el cliente.
public record PedidoRequest(
        @NotNull(message = "es obligatorio") Long especieId,
        @NotNull(message = "es obligatoria")
        @Min(value = 1, message = "debe ser al menos 1")
        @Max(value = 5, message = "no puede superar 5") Integer cantidad) {
}
