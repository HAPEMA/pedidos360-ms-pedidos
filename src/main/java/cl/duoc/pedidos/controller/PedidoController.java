package cl.duoc.pedidos.controller;

import cl.duoc.pedidos.dto.PedidoRequest;
import cl.duoc.pedidos.event.AdopcionConfirmadaEvent;
import cl.duoc.pedidos.event.AnimalAsignadoEvent;
import cl.duoc.pedidos.event.PedidoCreadoEvent;
import cl.duoc.pedidos.service.PedidoOperacion;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import cl.duoc.pedidos.messaging.EventoPublisher;
import cl.duoc.pedidos.dto.PedidoResponse;
import cl.duoc.pedidos.service.PedidoService;
import cl.duoc.pedidos.service.UsuarioActual;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/api/pedidos")
public class PedidoController {

    private static final Logger log = LoggerFactory.getLogger(PedidoController.class);

    private final PedidoService service;
    private final EventoPublisher eventos;

    public PedidoController(PedidoService service, EventoPublisher eventos) {
        this.service = service;
        this.eventos = eventos;
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
                                                Authentication auth) {
        PedidoResponse creado = service.crear(request, UsuarioActual.desde(auth), authorizationRecibido(auth));
        // service.crear ya hizo commit: el pedido existe en la BD. Si Rabbit falla, el publisher solo loguea.
        eventos.publicar(PedidoCreadoEvent.de(creado, subDe(auth)));
        // Location relativa: detrás del API Gateway el host interno del EC2 no debe filtrarse.
        return ResponseEntity.created(URI.create("/api/pedidos/" + creado.id())).body(creado);
    }

    @PostMapping("/{id}/asignar")
    public PedidoResponse asignar(@PathVariable Long id, Authentication auth) {
        PedidoOperacion op = service.asignar(id, UsuarioActual.desde(auth));
        // El estado ya está guardado; si Rabbit falla, el publisher solo loguea.
        publicarSiHaySub(op, () -> eventos.publicar(new AnimalAsignadoEvent(
                op.pedido().id(), op.pedido().especieId(), op.usuarioSub(), Instant.now())));
        return op.pedido();
    }

    @PostMapping("/{id}/confirmar")
    public PedidoResponse confirmar(@PathVariable Long id, Authentication auth) {
        PedidoOperacion op = service.confirmar(id, UsuarioActual.desde(auth));
        publicarSiHaySub(op, () -> eventos.publicar(new AdopcionConfirmadaEvent(
                op.pedido().id(), op.pedido().especieId(), op.pedido().especieNombre(),
                op.usuarioSub(), Instant.now())));
        return op.pedido();
    }

    /** Un pedido anterior a la columna creado_por_sub, operado por un admin, no tiene sub al que dirigir el evento. */
    private static void publicarSiHaySub(PedidoOperacion op, Runnable publicar) {
        if (op.usuarioSub() == null) {
            log.warn("Pedido {} sin sub de dueño: se guardó el cambio pero no se publica el evento", op.pedido().id());
            return;
        }
        publicar.run();
    }

    /**
     * Token relay: reconstruye el header Authorization con el mismo token que llegó en la request.
     * Se toma del contexto de seguridad (y no del header crudo) para reenviar solo un token que
     * este servicio ya validó.
     */
    private static String authorizationRecibido(Authentication auth) {
        if (auth instanceof JwtAuthenticationToken jwtAuth) {
            return "Bearer " + jwtAuth.getToken().getTokenValue();
        }
        throw new IllegalStateException("La request no viene autenticada con un JWT");
    }

    /** Claim "sub" del JWT ya validado (UUID estable del usuario en Cognito). */
    private static String subDe(Authentication auth) {
        return auth instanceof JwtAuthenticationToken jwtAuth ? jwtAuth.getToken().getSubject() : null;
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void eliminar(@PathVariable Long id) {
        service.eliminar(id);
    }
}
