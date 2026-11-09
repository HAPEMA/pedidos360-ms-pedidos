package cl.duoc.pedidos.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "pedido")
public class Pedido {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long especieId;

    // Copia del nombre al momento de comprar: el pedido no depende de cambios futuros del catálogo.
    @Column(nullable = false)
    private String especieNombre;

    @Column(nullable = false)
    private Integer cantidad;

    // Precio en CLP tomado de ms-catalogo, nunca del body.
    @Column(nullable = false)
    private Integer precioUnitario;

    @Column(nullable = false)
    private Integer total;

    // Se guarda como texto para que reordenar el enum no corrompa los datos.
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EstadoPedido estado;

    // Claim "username" del JWT, nunca del body.
    @Column(nullable = false)
    private String creadoPor;

    @Column(nullable = false)
    private Instant creadoEn;

    // Claim "sub" del dueño (UUID estable de Cognito). Nullable: los pedidos anteriores a esta columna no lo tienen.
    @Column(length = 64)
    private String creadoPorSub;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getEspecieId() { return especieId; }
    public void setEspecieId(Long especieId) { this.especieId = especieId; }

    public String getEspecieNombre() { return especieNombre; }
    public void setEspecieNombre(String especieNombre) { this.especieNombre = especieNombre; }

    public Integer getCantidad() { return cantidad; }
    public void setCantidad(Integer cantidad) { this.cantidad = cantidad; }

    public Integer getPrecioUnitario() { return precioUnitario; }
    public void setPrecioUnitario(Integer precioUnitario) { this.precioUnitario = precioUnitario; }

    public Integer getTotal() { return total; }
    public void setTotal(Integer total) { this.total = total; }

    public EstadoPedido getEstado() { return estado; }
    public void setEstado(EstadoPedido estado) { this.estado = estado; }

    public String getCreadoPor() { return creadoPor; }
    public void setCreadoPor(String creadoPor) { this.creadoPor = creadoPor; }

    public String getCreadoPorSub() { return creadoPorSub; }
    public void setCreadoPorSub(String creadoPorSub) { this.creadoPorSub = creadoPorSub; }

    public Instant getCreadoEn() { return creadoEn; }
    public void setCreadoEn(Instant creadoEn) { this.creadoEn = creadoEn; }
}
