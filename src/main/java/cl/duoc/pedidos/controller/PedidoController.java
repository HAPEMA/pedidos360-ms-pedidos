package cl.duoc.pedidos.controller;

import cl.duoc.pedidos.dto.PedidoRequest;
import cl.duoc.pedidos.dto.PedidoResponse;
import cl.duoc.pedidos.service.PedidoService;
import cl.duoc.pedidos.service.UsuarioActual;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/pedidos")
public class PedidoController {

    private final PedidoService service;

    public PedidoController(PedidoService service) {
        this.service = service;
    }

    @GetMapping
    public List<PedidoResponse> listar(Authentication auth) {
        return service.listar(UsuarioActual.desde(auth));
    }

    @GetMapping("/{id}")
    public PedidoResponse obtener(@PathVariable Long id, Authentication auth) {
        return service.obtener(id, UsuarioActual.desde(auth));
    }

    @PostMapping
    public ResponseEntity<PedidoResponse> crear(@Valid @RequestBody PedidoRequest request,
                                                Authentication auth,
                                                @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization) {
        // El header Authorization se reenvía tal cual a ms-catalogo (token relay).
        PedidoResponse creado = service.crear(request, UsuarioActual.desde(auth), authorization);
        // Location relativa: detrás del API Gateway el host interno del EC2 no debe filtrarse.
        return ResponseEntity.created(URI.create("/api/pedidos/" + creado.id())).body(creado);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void eliminar(@PathVariable Long id) {
        service.eliminar(id);
    }
}
