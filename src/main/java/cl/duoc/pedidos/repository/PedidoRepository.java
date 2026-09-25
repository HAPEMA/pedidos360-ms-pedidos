package cl.duoc.pedidos.repository;

import cl.duoc.pedidos.model.Pedido;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PedidoRepository extends JpaRepository<Pedido, Long> {

    List<Pedido> findAllByOrderByCreadoEnDescIdDesc();

    List<Pedido> findByCreadoPorOrderByCreadoEnDescIdDesc(String creadoPor);
}
