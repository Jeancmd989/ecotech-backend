package com.upc.appecotech.interfaces;

import com.upc.appecotech.dtos.ProductoDTO;
import com.upc.appecotech.entidades.Producto;

import java.util.List;

public interface IProductoService {
    // Métodos CRUD básicos
    ProductoDTO crearProducto(ProductoDTO productoDTO);
    ProductoDTO actualizarProducto(Long id, ProductoDTO productoDTO);
    ProductoDTO buscarPorId(Long id);
    List<ProductoDTO> listarTodos();
    void eliminarProducto(Long id);

    // Métodos de ordenamiento por puntos
    List<ProductoDTO> listarPorPuntosAscendente();
    List<ProductoDTO> listarPorPuntosDescendente();
    List<ProductoDTO> listarPorRangoPuntos(Integer min, Integer max);

    // Nuevos métodos para filtrar por los nuevos atributos
    List<ProductoDTO> listarPorCategoria(String categoria);
    List<ProductoDTO> listarPorEstado(String estado);
    List<ProductoDTO> listarProductosActivos();
    List<ProductoDTO> listarProductosDisponibles();
    List<ProductoDTO> listarPorCategoriaYEstado(String categoria, String estado);
    List<ProductoDTO> listarProductosActivosPorCategoria(String categoria);
    List<String> listarCategorias();

    // Método para actualizar stock
    ProductoDTO actualizarStock(Long id, Integer nuevoStock);

    // Método para cambiar estado
    ProductoDTO cambiarEstado(Long id, String nuevoEstado);

    ProductoDTO actualizarPermiteEntregaDigital(Long id, Boolean permiteEntregaDigital);
    List<ProductoDTO> listarProductosDigitales();
}
