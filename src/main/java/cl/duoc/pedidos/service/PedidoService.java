package cl.duoc.pedidos.service;

import cl.duoc.pedidos.client.CatalogoClient;
import cl.duoc.pedidos.dto.EspecieDto;
import cl.duoc.pedidos.dto.PedidoRequest;
import cl.duoc.pedidos.dto.PedidoResponse;
import cl.duoc.pedidos.model.EstadoPedido;
import cl.duoc.pedidos.model.Pedido;
import cl.duoc.pedidos.repository.PedidoRepository;
import cl.duoc.pedidos.web.AccesoDenegadoException;
import cl.duoc.pedidos.web.CatalogoNoDisponibleException;
import cl.duoc.pedidos.web.RecursoNoEncontradoException;
import cl.duoc.pedidos.web.StockInsuficienteException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
public class PedidoService {

    private final PedidoRepository repository;
    private final CatalogoClient catalogoClient;

    public PedidoService(PedidoRepository repository, CatalogoClient catalogoClient) {
        this.repository = repository;
        this.catalogoClient = catalogoClient;
    }

    @Transactional(readOnly = true)
    public List<PedidoResponse> listar(UsuarioActual usuario) {
        // Filtrado en el servidor: el cliente no puede pedir pedidos ajenos cambiando un parámetro.
        List<Pedido> pedidos = usuario.esAdmin()
                ? repository.findAllByOrderByCreadoEnDescIdDesc()
                : repository.findByCreadoPorOrderByCreadoEnDescIdDesc(usuario.username());
        return pedidos.stream().map(PedidoResponse::desde).toList();
    }

    @Transactional(readOnly = true)
    public PedidoResponse obtener(Long id, UsuarioActual usuario) {
        Pedido pedido = buscar(id);
        // Control de acceso a nivel de objeto (evita IDOR): el scope read no basta para ver pedidos ajenos.
        if (!usuario.esAdmin() && !pedido.getCreadoPor().equals(usuario.username())) {
            throw new AccesoDenegadoException("No tienes permiso para ver este pedido");
        }
        return PedidoResponse.desde(pedido);
    }

    @Transactional
    public PedidoResponse crear(PedidoRequest request, UsuarioActual usuario, String authorization) {
        // Precio, stock y nombre salen del catálogo (fuente de verdad), nunca del body.
        EspecieDto especie = catalogoClient.obtenerEspecie(request.especieId(), authorization);
        if (especie.precio() == null || especie.stock() == null) {
            throw new CatalogoNoDisponibleException("ms-catalogo respondió sin precio o stock", null);
        }
        if (especie.stock() < request.cantidad()) {
            throw new StockInsuficienteException(
                    "Solo quedan " + especie.stock() + " ejemplares de " + especie.nombreEs());
        }

        Pedido pedido = new Pedido();
        pedido.setEspecieId(request.especieId());
        pedido.setEspecieNombre(especie.nombreEs());
        pedido.setCantidad(request.cantidad());
        pedido.setPrecioUnitario(especie.precio());
        pedido.setTotal(Math.multiplyExact(especie.precio(), request.cantidad()));
        pedido.setEstado(EstadoPedido.PENDIENTE);
        pedido.setCreadoPor(usuario.username());
        pedido.setCreadoEn(Instant.now());
        return PedidoResponse.desde(repository.save(pedido));
    }

    @Transactional
    public void eliminar(Long id) {
        repository.delete(buscar(id));
    }

    private Pedido buscar(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("Pedido " + id + " no encontrado"));
    }
}
