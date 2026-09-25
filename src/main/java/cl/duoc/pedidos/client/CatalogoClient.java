package cl.duoc.pedidos.client;

import cl.duoc.pedidos.dto.EspecieDto;
import cl.duoc.pedidos.web.CatalogoNoDisponibleException;
import cl.duoc.pedidos.web.RecursoNoEncontradoException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Cliente de ms-catalogo. Aplica token relay: reenvía el mismo header Authorization
 * que recibió este servicio, así ms-catalogo valida al usuario real y no hace falta
 * una credencial de servicio compartida (ni un canal "de confianza" sin autenticación).
 */
@Component
public class CatalogoClient {

    private final RestClient catalogoRestClient;

    public CatalogoClient(RestClient catalogoRestClient) {
        this.catalogoRestClient = catalogoRestClient;
    }

    public EspecieDto obtenerEspecie(Long id, String authorization) {
        try {
            EspecieDto especie = catalogoRestClient.get()
                    .uri("/api/especies/{id}", id)
                    .header(HttpHeaders.AUTHORIZATION, authorization)
                    .retrieve()
                    .body(EspecieDto.class);
            if (especie == null) {
                throw new CatalogoNoDisponibleException("ms-catalogo respondió sin contenido", null);
            }
            return especie;
        } catch (HttpClientErrorException e) {
            if (e.getStatusCode().value() == HttpStatus.NOT_FOUND.value()) {
                throw new RecursoNoEncontradoException("La especie " + id + " no existe en el catálogo");
            }
            // Un 401/403 del catálogo tampoco se reenvía tal cual: para el cliente es una falla del upstream.
            throw new CatalogoNoDisponibleException("ms-catalogo rechazó la consulta", e);
        } catch (RestClientException e) {
            // 5xx, timeout, conexión rechazada o respuesta ilegible → 502 Bad Gateway.
            throw new CatalogoNoDisponibleException("ms-catalogo no está disponible", e);
        }
    }
}
