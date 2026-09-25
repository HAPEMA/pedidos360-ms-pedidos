package cl.duoc.pedidos.client;

import cl.duoc.pedidos.dto.EspecieDto;
import cl.duoc.pedidos.web.CatalogoNoDisponibleException;
import cl.duoc.pedidos.web.RecursoNoEncontradoException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.net.SocketTimeoutException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class CatalogoClientTest {

    private static final String TOKEN = "Bearer token-de-prueba";

    private MockRestServiceServer servidor;
    private CatalogoClient cliente;

    @BeforeEach
    void preparar() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://catalogo");
        servidor = MockRestServiceServer.bindTo(builder).build();
        cliente = new CatalogoClient(builder.build());
    }

    @Test
    void reenviaAuthorizationYLeeLosCamposNecesarios() {
        servidor.expect(requestTo("http://catalogo/api/especies/1"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header(HttpHeaders.AUTHORIZATION, TOKEN))
                .andRespond(withSuccess("""
                        {"id":1,"nombreApi":"Cheetah","nombreEs":"Guepardo","nombreCientifico":"Acinonyx jubatus",
                         "habitat":"Sabana","dieta":"Carnívoro","velocidadMaxima":"110 km/h","esperanzaVida":"12 años",
                         "ubicaciones":["Africa"],"precio":2500000,"stock":3}
                        """, MediaType.APPLICATION_JSON));

        EspecieDto especie = cliente.obtenerEspecie(1L, TOKEN);

        assertThat(especie).isEqualTo(new EspecieDto(1L, "Guepardo", 2500000, 3));
        servidor.verify();
    }

    @Test
    void un404DelCatalogoSeTraduceA404() {
        servidor.expect(requestTo("http://catalogo/api/especies/99"))
                .andRespond(withStatus(HttpStatus.NOT_FOUND)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"status\":404,\"error\":\"Especie no encontrada\"}"));

        assertThatThrownBy(() -> cliente.obtenerEspecie(99L, TOKEN))
                .isInstanceOf(RecursoNoEncontradoException.class);
    }

    @Test
    void unErrorDelCatalogoSeTraduceA502() {
        servidor.expect(requestTo("http://catalogo/api/especies/1")).andRespond(withServerError());

        assertThatThrownBy(() -> cliente.obtenerEspecie(1L, TOKEN))
                .isInstanceOf(CatalogoNoDisponibleException.class);
    }

    @Test
    void unTimeoutDelCatalogoSeTraduceA502() {
        servidor.expect(requestTo("http://catalogo/api/especies/1"))
                .andRespond(withException(new SocketTimeoutException("Read timed out")));

        assertThatThrownBy(() -> cliente.obtenerEspecie(1L, TOKEN))
                .isInstanceOf(CatalogoNoDisponibleException.class);
    }
}
