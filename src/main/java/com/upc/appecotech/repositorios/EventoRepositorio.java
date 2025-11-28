package com.upc.appecotech.repositorios;

import com.upc.appecotech.entidades.Evento;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;


public interface EventoRepositorio extends JpaRepository<Evento, Long> {
    List<Evento> findByFechaGreaterThanEqualOrderByFechaAsc(LocalDate fecha);

    List<Evento> findByFechaLessThanOrderByFechaDesc(LocalDate fecha);

    @Query("SELECT e.nombre, COUNT(ue), e.fecha " +
            "FROM Evento e " +
            "LEFT JOIN Usuarioevento ue ON ue.idevento.id = e.id " +
            "GROUP BY e.id, e.nombre, e.fecha " +
            "ORDER BY COUNT(ue) DESC")
    List<Object[]> obtenerEventosMasParticipados(Pageable pageable);


    // Nuevos métodos para los nuevos atributos
    List<Evento> findByEstadoEvento(String estadoEvento);
    List<Evento> findByCategoria(String categoria);
    List<Evento> findByEstadoEventoOrderByFechaAsc(String estadoEvento);
    List<Evento> findByCategoriaAndEstadoEvento(String categoria, String estadoEvento);

    // Consultas personalizadas
    @Query("SELECT e FROM Evento e WHERE e.estadoEvento = 'programado' AND e.fecha >= :fechaActual ORDER BY e.fecha ASC")
    List<Evento> findEventosProgramados(@Param("fechaActual") LocalDate fechaActual);

    @Query("SELECT e FROM Evento e WHERE e.estadoEvento = 'en curso' ORDER BY e.fecha DESC")
    List<Evento> findEventosEnCurso();

    @Query("SELECT e FROM Evento e WHERE e.estadoEvento = 'finalizado' ORDER BY e.fecha DESC")
    List<Evento> findEventosFinalizados();

    @Query("SELECT DISTINCT e.categoria FROM Evento e WHERE e.categoria IS NOT NULL ORDER BY e.categoria")
    List<String> findAllCategorias();

    @Query("SELECT e FROM Evento e WHERE e.categoria = :categoria AND e.estadoEvento = 'programado' AND e.fecha >= :fechaActual ORDER BY e.fecha ASC")
    List<Evento> findEventosProgramadosPorCategoria(@Param("categoria") String categoria, @Param("fechaActual") LocalDate fechaActual);

    // Buscar eventos con capacidad disponible
    @Query("SELECT e FROM Evento e WHERE e.capacidadMaxima > 0 AND e.estadoEvento = 'programado' ORDER BY e.fecha ASC")
    List<Evento> findEventosConCapacidadDisponible();

    // Contar inscritos por evento (si tienes una relación con inscripciones)
    @Query("SELECT COUNT(i) FROM Usuarioevento i WHERE i.idevento.id = :eventoId")
    Long contarInscritosPorEvento(@Param("eventoId") Long eventoId);
}

