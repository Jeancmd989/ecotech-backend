package com.upc.appecotech.servicios;

import com.upc.appecotech.dtos.CanjeUsuarioDTO;
import com.upc.appecotech.dtos.SuscripcionDTO;
import com.upc.appecotech.entidades.Canjeusuario;
import com.upc.appecotech.entidades.Historialdepunto;
import com.upc.appecotech.entidades.Producto;
import com.upc.appecotech.security.entidades.Usuario;
import com.upc.appecotech.interfaces.ICanjeusuarioService;
import com.upc.appecotech.interfaces.ISuscripcionService;
import com.upc.appecotech.repositorios.CanjeUsuarioRepositorio;
import com.upc.appecotech.repositorios.HistorialPuntosRepository;
import com.upc.appecotech.repositorios.ProductoRepositorio;
import com.upc.appecotech.security.repositorios.UsuarioRepositorio;
import jakarta.persistence.EntityNotFoundException;
import jakarta.transaction.Transactional;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;

@Service
public class CanjeusuarioService implements ICanjeusuarioService {
    @Autowired
    private CanjeUsuarioRepositorio canjeUsuarioRepositorio;

    @Autowired
    private UsuarioRepositorio usuarioRepositorio;

    @Autowired
    private ProductoRepositorio productoRepositorio;

    @Autowired
    private HistorialPuntosRepository historialPuntosRepository;

    @Autowired
    private ISuscripcionService suscripcionService;

    @Autowired
    private NotificacionService notificacionService;

    @Autowired
    private ModelMapper modelMapper;

    private static final List<String> ESTADOS_CANJE_VALIDOS = Arrays.asList("pendiente", "aprobado", "entregado", "rechazado");
    private static final List<String> METODOS_ENTREGA_VALIDOS = Arrays.asList("digital", "físico", "retiro en tienda");

    @Override
    @Transactional
    public CanjeUsuarioDTO canjearProducto(CanjeUsuarioDTO canjeUsuarioDTO) {
        try {
            Usuario usuario = usuarioRepositorio.findById(canjeUsuarioDTO.getIdUsuario())
                    .orElseThrow(() -> new EntityNotFoundException("Usuario no encontrado con ID: " + canjeUsuarioDTO.getIdUsuario()));

            Producto producto = productoRepositorio.findById(canjeUsuarioDTO.getIdProducto())
                    .orElseThrow(() -> new EntityNotFoundException("Producto no encontrado con ID: " + canjeUsuarioDTO.getIdProducto()));

            // Validar stock del producto
            if (producto.getStock() < canjeUsuarioDTO.getCantidad()) {
                throw new RuntimeException("Stock insuficiente. Stock disponible: " + producto.getStock());
            }

            // Validar estado del producto
            if (!"activo".equals(producto.getEstado())) {
                throw new RuntimeException("El producto no está disponible para canje");
            }

            // ✅ Valida por el campo booleano del producto
            if ("digital".equalsIgnoreCase(canjeUsuarioDTO.getMetodoEntrega())) {
                if (producto.getPermiteEntregaDigital() == null || !producto.getPermiteEntregaDigital()) {
                    throw new IllegalArgumentException(
                            "Este producto no permite entrega digital. Solo está disponible para productos digitales como gift cards, software, vales, etc."
                    );
                }
            }

            // 🆕 VALIDAR DIRECCIÓN SI ES ENTREGA FÍSICA
            if ("físico".equalsIgnoreCase(canjeUsuarioDTO.getMetodoEntrega())) {
                if (canjeUsuarioDTO.getDireccionEntrega() == null || canjeUsuarioDTO.getDireccionEntrega().trim().isEmpty()) {
                    throw new IllegalArgumentException("La dirección de entrega es obligatoria para envíos físicos");
                }
                if (canjeUsuarioDTO.getCiudad() == null || canjeUsuarioDTO.getCiudad().trim().isEmpty()) {
                    throw new IllegalArgumentException("La ciudad es obligatoria para envíos físicos");
                }
                if (canjeUsuarioDTO.getTelefono() == null || canjeUsuarioDTO.getTelefono().trim().isEmpty()) {
                    throw new IllegalArgumentException("El teléfono es obligatorio para envíos físicos");
                }
            }

            // CALCULAR PUNTOS CON DESCUENTO DEL PLAN
            int puntosBaseProducto = producto.getPuntosrequerido();
            int descuentoPlan = obtenerDescuentoCanje(usuario.getId());
            int puntosConDescuento = calcularPuntosConDescuento(puntosBaseProducto, descuentoPlan);
            int puntosRequeridosTotal = puntosConDescuento * canjeUsuarioDTO.getCantidad();

            // Validar con los puntos descontados
            if (!validarPuntosUsuario(usuario.getId(), puntosRequeridosTotal)) {
                throw new RuntimeException("Puntos insuficientes para realizar el canje. Necesitas: " + puntosRequeridosTotal + " puntos");
            }

            // Validar estado y método de entrega
            if (canjeUsuarioDTO.getEstadoCanje() != null && !ESTADOS_CANJE_VALIDOS.contains(canjeUsuarioDTO.getEstadoCanje().toLowerCase())) {
                throw new IllegalArgumentException("Estado de canje inválido");
            }

            if (canjeUsuarioDTO.getMetodoEntrega() != null && !METODOS_ENTREGA_VALIDOS.contains(canjeUsuarioDTO.getMetodoEntrega().toLowerCase())) {
                throw new IllegalArgumentException("Método de entrega inválido");
            }

            Canjeusuario canjeUsuario = new Canjeusuario();
            canjeUsuario.setIdusuario(usuario);
            canjeUsuario.setIdproducto(producto);
            canjeUsuario.setFechacanje(LocalDate.now());
            canjeUsuario.setCantidad(canjeUsuarioDTO.getCantidad());
            canjeUsuario.setEstadoCanje(canjeUsuarioDTO.getEstadoCanje() != null ? canjeUsuarioDTO.getEstadoCanje() : "pendiente");
            canjeUsuario.setMetodoEntrega(canjeUsuarioDTO.getMetodoEntrega());
            canjeUsuario.setObservaciones(canjeUsuarioDTO.getObservaciones());

            // 🆕 GUARDAR DIRECCIÓN DE ENTREGA
            canjeUsuario.setDireccionEntrega(canjeUsuarioDTO.getDireccionEntrega());
            canjeUsuario.setCiudad(canjeUsuarioDTO.getCiudad());
            canjeUsuario.setCodigoPostal(canjeUsuarioDTO.getCodigoPostal());
            canjeUsuario.setTelefono(canjeUsuarioDTO.getTelefono());
            canjeUsuario.setReferencia(canjeUsuarioDTO.getReferencia());

            Canjeusuario guardado = canjeUsuarioRepositorio.save(canjeUsuario);

            // Descontar stock del producto
            producto.setStock(producto.getStock() - canjeUsuarioDTO.getCantidad());
            if (producto.getStock() == 0) {
                producto.setEstado("agotado");
            }
            productoRepositorio.save(producto);

            // REGISTRAR PUNTOS DESCONTADOS
            String planUsuario = obtenerNombrePlan(usuario.getId());
            String descripcionDescuento = descuentoPlan > 0
                    ? String.format(" (-%d%% descuento %s)", descuentoPlan, planUsuario)
                    : "";

            Historialdepunto historial = new Historialdepunto();
            historial.setIdusuario(usuario);
            historial.setPuntosobtenidos(0);
            historial.setPuntoscanjeados(puntosRequeridosTotal);
            historial.setTipomovimiento("Canje");
            historial.setDescripcion(
                    String.format("Canje de producto: %s (Cantidad: %d)%s",
                            producto.getNombre(),
                            canjeUsuarioDTO.getCantidad(),
                            descripcionDescuento)
            );
            historial.setFecha(LocalDate.now());

            historialPuntosRepository.save(historial);

            // 🆕 CREAR NOTIFICACIÓN DE CANJE PENDIENTE
            try {
                notificacionService.crearNotificacionCanje(
                        usuario.getId(),
                        "canje_pendiente",
                        "Canje registrado",
                        String.format("Tu canje de %s está pendiente de aprobación", producto.getNombre()),
                        guardado.getId()
                );
            } catch (Exception e) {
                System.err.println("Error al crear notificación: " + e.getMessage());
            }

            return modelMapper.map(guardado, CanjeUsuarioDTO.class);

        } catch (EntityNotFoundException e) {
            throw new RuntimeException("Error al realizar canje: " + e.getMessage());
        }
    }

    @Transactional
    public CanjeUsuarioDTO actualizarEstadoCanje(Long id, String nuevoEstado) {
        if (!ESTADOS_CANJE_VALIDOS.contains(nuevoEstado.toLowerCase())) {
            throw new IllegalArgumentException("Estado de canje inválido");
        }

        return canjeUsuarioRepositorio.findById(id)
                .map(canje -> {
                    String estadoAnterior = canje.getEstadoCanje();
                    canje.setEstadoCanje(nuevoEstado);

                    if ("rechazado".equals(nuevoEstado) && !"rechazado".equals(estadoAnterior)) {
                        devolverPuntosYStock(canje);
                    }

                    Canjeusuario actualizado = canjeUsuarioRepositorio.save(canje);

                    // 🆕 CREAR NOTIFICACIÓN SEGÚN EL NUEVO ESTADO
                    try {
                        String tipoNotificacion = "canje_" + nuevoEstado.toLowerCase();
                        String titulo = obtenerTituloNotificacion(nuevoEstado);
                        String mensaje = obtenerMensajeNotificacion(nuevoEstado, canje.getIdproducto().getNombre());

                        notificacionService.crearNotificacionCanje(
                                canje.getIdusuario().getId(),
                                tipoNotificacion,
                                titulo,
                                mensaje,
                                actualizado.getId()
                        );
                    } catch (Exception e) {
                        System.err.println("Error al crear notificación: " + e.getMessage());
                    }

                    return modelMapper.map(actualizado, CanjeUsuarioDTO.class);
                })
                .orElseThrow(() -> new EntityNotFoundException("Canje no encontrado con ID: " + id));
    }

    // 🆕 MÉTODOS HELPER PARA NOTIFICACIONES
    private String obtenerTituloNotificacion(String estado) {
        return switch (estado.toLowerCase()) {
            case "aprobado" -> "¡Canje aprobado!";
            case "entregado" -> "Producto entregado";
            case "rechazado" -> "Canje rechazado";
            default -> "Actualización de canje";
        };
    }

    private String obtenerMensajeNotificacion(String estado, String nombreProducto) {
        return switch (estado.toLowerCase()) {
            case "aprobado" -> String.format("Tu canje de %s ha sido aprobado y será procesado pronto", nombreProducto);
            case "entregado" -> String.format("Tu producto %s ha sido entregado. ¡Disfrútalo!", nombreProducto);
            case "rechazado" -> String.format("Tu canje de %s ha sido rechazado. Los puntos fueron devueltos", nombreProducto);
            default -> String.format("Hay una actualización en tu canje de %s", nombreProducto);
        };
    }

    // ... resto de métodos sin cambios ...

    @Override
    @Transactional
    public boolean validarPuntosUsuario(Long idUsuario, Long idProducto, Integer cantidad) {
        Usuario usuario = usuarioRepositorio.findById(idUsuario)
                .orElseThrow(() -> new EntityNotFoundException("Usuario no encontrado"));

        Producto producto = productoRepositorio.findById(idProducto)
                .orElseThrow(() -> new EntityNotFoundException("Producto no encontrado"));

        int puntosBaseProducto = producto.getPuntosrequerido();
        int descuentoPlan = obtenerDescuentoCanje(idUsuario);
        int puntosConDescuento = calcularPuntosConDescuento(puntosBaseProducto, descuentoPlan);
        int puntosRequeridos = puntosConDescuento * cantidad;

        List<Historialdepunto> historial = historialPuntosRepository.findByUsuarioId(idUsuario);
        int puntosObtenidos = historial.stream().mapToInt(Historialdepunto::getPuntosobtenidos).sum();
        int puntosCanjeados = historial.stream().mapToInt(Historialdepunto::getPuntoscanjeados).sum();
        int puntosDisponibles = puntosObtenidos - puntosCanjeados;

        return puntosDisponibles >= puntosRequeridos;
    }

    // ✅ Valida por el campo booleano
    public boolean permiteEntregaDigital(Long idProducto) {
        Producto producto = productoRepositorio.findById(idProducto)
                .orElseThrow(() -> new EntityNotFoundException("Producto no encontrado"));

        return producto.getPermiteEntregaDigital() != null && producto.getPermiteEntregaDigital();
    }

    private boolean validarPuntosUsuario(Long idUsuario, int puntosRequeridos) {
        List<Historialdepunto> historial = historialPuntosRepository.findByUsuarioId(idUsuario);
        int puntosObtenidos = historial.stream().mapToInt(Historialdepunto::getPuntosobtenidos).sum();
        int puntosCanjeados = historial.stream().mapToInt(Historialdepunto::getPuntoscanjeados).sum();
        int puntosDisponibles = puntosObtenidos - puntosCanjeados;

        return puntosDisponibles >= puntosRequeridos;
    }

    private int obtenerDescuentoCanje(Long idUsuario) {
        SuscripcionDTO suscripcion = suscripcionService.obtenerSuscripcionActivaUsuario(idUsuario);

        if (suscripcion == null) {
            return 0;
        }

        return switch (suscripcion.getTipoplan()) {
            case "Premium" -> 15;
            case "VIP" -> 30;
            default -> 0;
        };
    }

    private int calcularPuntosConDescuento(int puntosBase, int descuentoPorcentaje) {
        if (descuentoPorcentaje == 0) {
            return puntosBase;
        }

        double descuento = (double) descuentoPorcentaje / 100;
        return (int) Math.ceil(puntosBase * (1 - descuento));
    }

    private String obtenerNombrePlan(Long idUsuario) {
        SuscripcionDTO suscripcion = suscripcionService.obtenerSuscripcionActivaUsuario(idUsuario);
        return suscripcion != null ? suscripcion.getTipoplan() : "Básico";
    }

    @Override
    @Transactional
    public CanjeUsuarioDTO buscarPorId(Long id) {
        return canjeUsuarioRepositorio.findById(id)
                .map(canjeUsuario -> modelMapper.map(canjeUsuario, CanjeUsuarioDTO.class))
                .orElse(null);
    }
    @Override
    @Transactional
    public List<CanjeUsuarioDTO> listarTodos() {
        List<Canjeusuario> lista = canjeUsuarioRepositorio.findAll();
        return lista.stream()
                .map(canjeUsuario -> modelMapper.map(canjeUsuario, CanjeUsuarioDTO.class))
                .toList();
    }

    @Override
    @Transactional
    public List<CanjeUsuarioDTO> listarCanjesPorUsuario(Long idUsuario) {
        usuarioRepositorio.findById(idUsuario)
                .orElseThrow(() -> new EntityNotFoundException("Usuario no encontrado con ID: " + idUsuario));

        List<Canjeusuario> lista = canjeUsuarioRepositorio.findByUsuarioId(idUsuario);

        return lista.stream()
                .map(canjeUsuario -> modelMapper.map(canjeUsuario, CanjeUsuarioDTO.class))
                .toList();
    }

    @Override
    @Transactional
    public int obtenerPuntosDisponibles(Long idUsuario) {
        Usuario usuario = usuarioRepositorio.findById(idUsuario)
                .orElseThrow(() -> new EntityNotFoundException("Usuario no encontrado"));

        List<Historialdepunto> historial = historialPuntosRepository.findByUsuarioId(idUsuario);

        int puntosObtenidos = historial.stream().mapToInt(Historialdepunto::getPuntosobtenidos).sum();
        int puntosCanjeados = historial.stream().mapToInt(Historialdepunto::getPuntoscanjeados).sum();

        return puntosObtenidos - puntosCanjeados;
    }

    @Transactional
    public CanjeUsuarioDTO actualizarMetodoEntrega(Long id, String nuevoMetodo) {
        if (!METODOS_ENTREGA_VALIDOS.contains(nuevoMetodo.toLowerCase())) {
            throw new IllegalArgumentException("Método de entrega inválido");
        }

        return canjeUsuarioRepositorio.findById(id)
                .map(canje -> {
                    if ("digital".equalsIgnoreCase(nuevoMetodo)) {
                        Producto producto = canje.getIdproducto();
                        if (producto.getPermiteEntregaDigital() == null || !producto.getPermiteEntregaDigital()) {
                            throw new IllegalArgumentException(
                                    "No se puede cambiar a entrega digital. Este producto no permite entrega digital"
                            );
                        }
                    }

                    canje.setMetodoEntrega(nuevoMetodo);
                    Canjeusuario actualizado = canjeUsuarioRepositorio.save(canje);
                    return modelMapper.map(actualizado, CanjeUsuarioDTO.class);
                })
                .orElseThrow(() -> new EntityNotFoundException("Canje no encontrado con ID: " + id));
    }

    @Transactional
    public CanjeUsuarioDTO agregarObservaciones(Long id, String observaciones) {
        return canjeUsuarioRepositorio.findById(id)
                .map(canje -> {
                    canje.setObservaciones(observaciones);
                    Canjeusuario actualizado = canjeUsuarioRepositorio.save(canje);
                    return modelMapper.map(actualizado, CanjeUsuarioDTO.class);
                })
                .orElseThrow(() -> new EntityNotFoundException("Canje no encontrado con ID: " + id));
    }

    @Transactional
    public List<CanjeUsuarioDTO> listarCanjesPorEstado(String estado) {
        if (!ESTADOS_CANJE_VALIDOS.contains(estado.toLowerCase())) {
            throw new IllegalArgumentException("Estado de canje inválido");
        }

        List<Canjeusuario> lista = canjeUsuarioRepositorio.findByEstadoCanje(estado);
        return lista.stream()
                .map(canjeUsuario -> modelMapper.map(canjeUsuario, CanjeUsuarioDTO.class))
                .toList();
    }

    private void devolverPuntosYStock(Canjeusuario canje) {
        Producto producto = canje.getIdproducto();
        producto.setStock(producto.getStock() + canje.getCantidad());

        if ("agotado".equals(producto.getEstado()) && producto.getStock() > 0) {
            producto.setEstado("activo");
        }

        productoRepositorio.save(producto);

        int descuentoPlan = obtenerDescuentoCanje(canje.getIdusuario().getId());
        int puntosConDescuento = calcularPuntosConDescuento(producto.getPuntosrequerido(), descuentoPlan);
        int puntosADevolver = puntosConDescuento * canje.getCantidad();

        Historialdepunto historial = new Historialdepunto();
        historial.setIdusuario(canje.getIdusuario());
        historial.setPuntosobtenidos(puntosADevolver);
        historial.setPuntoscanjeados(0);
        historial.setTipomovimiento("Devolución");
        historial.setDescripcion("Devolución por canje rechazado: " + producto.getNombre() + " (Cantidad: " + canje.getCantidad() + ")");
        historial.setFecha(LocalDate.now());

        historialPuntosRepository.save(historial);
    }
}