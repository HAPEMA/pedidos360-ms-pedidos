package cl.duoc.pedidos.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

// Solo los campos que este servicio necesita; el resto de la ficha se ignora.
@JsonIgnoreProperties(ignoreUnknown = true)
public record EspecieDto(Long id, String nombreEs, Integer precio, Integer stock) {
}
