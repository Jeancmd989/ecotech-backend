package com.upc.appecotech.repositorios;

import com.upc.appecotech.entidades.Producto;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ProductoRepositorio extends JpaRepository<Producto, Long> {
    List<Producto> findAllByOrderByPuntosrequeridoAsc();
    List<Producto> findAllByOrderByPuntosrequeridoDesc();
    List<Producto> findByPuntosrequeridoBetween(Integer min, Integer max);


    // Nuevos métodos para los nuevos atributos
    List<Producto> findByCategoria(String categoria);
    List<Producto> findByEstado(String estado);
    List<Producto> findByEstadoOrderByPuntosrequeridoAsc(String estado);
    List<Producto> findByStockGreaterThan(Integer stock);
    List<Producto> findByCategoriaAndEstado(String categoria, String estado);

    // Consultas personalizadas
    @Query("SELECT p FROM Producto p WHERE p.estado = 'activo' ORDER BY p.puntosrequerido ASC")
    List<Producto> findProductosActivos();

    @Query("SELECT p FROM Producto p WHERE p.stock > 0 AND p.estado != 'inactivo'")
    List<Producto> findProductosDisponibles();

    @Query("SELECT DISTINCT p.categoria FROM Producto p WHERE p.categoria IS NOT NULL ORDER BY p.categoria")
    List<String> findAllCategorias();

    @Query("SELECT p FROM Producto p WHERE p.categoria = :categoria AND p.estado = 'activo' ORDER BY p.puntosrequerido ASC")
    List<Producto> findProductosActivosPorCategoria(@Param("categoria") String categoria);

    // 🆕 NUEVO: Productos que permiten entrega digital
    List<Producto> findByPermiteEntregaDigital(Boolean permiteEntregaDigital);

    // 🆕 NUEVO: Productos digitales disponibles
    @Query("SELECT p FROM Producto p WHERE p.permiteEntregaDigital = true AND p.estado = 'activo' AND p.stock > 0")
    List<Producto> findProductosDigitalesDisponibles();



}
