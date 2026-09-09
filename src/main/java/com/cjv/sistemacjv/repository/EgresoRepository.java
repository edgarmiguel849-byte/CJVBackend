package com.cjv.sistemacjv.repository;

import com.cjv.sistemacjv.entity.Egreso;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface EgresoRepository extends JpaRepository<Egreso, Integer> {

    // Egresos de un rango de fechas (para el cierre de caja diario)
    List<Egreso> findByActivoTrueAndFechaEgresoBetween(LocalDate desde, LocalDate hasta);

    // Egresos de un día específico
    List<Egreso> findByActivoTrueAndFechaEgreso(LocalDate fecha);

    /**
     * Los egresos que UNA persona capturó en UN día.
     *
     * Cada quien entrega lo que recibió MENOS lo que sacó. Si Tete pagó una
     * mensajería de su cajón, ese dinero ya no está y tiene que salir de su
     * corte, no del de Adri.
     */
    List<Egreso> findByActivoTrueAndFechaEgresoAndUsuario_IdUsuarioOrderByIdEgresoAsc(
            LocalDate fecha, Integer idUsuario);

    /**
     * Búsqueda paginada para la pantalla de Egresos.
     * Los tres filtros son opcionales: si llegan en null, no se aplican.
     * El texto se compara contra concepto, comentarios y quién lo registró.
     */
    @Query("""
            SELECT e FROM Egreso e
            WHERE e.activo = true
              AND (:desde IS NULL OR e.fechaEgreso >= :desde)
              AND (:hasta IS NULL OR e.fechaEgreso <= :hasta)
              AND (:texto IS NULL
                   OR LOWER(e.concepto) LIKE LOWER(CONCAT('%', :texto, '%'))
                   OR LOWER(COALESCE(e.comentarios, '')) LIKE LOWER(CONCAT('%', :texto, '%'))
                   OR LOWER(e.usuario.nombreUsuario) LIKE LOWER(CONCAT('%', :texto, '%')))
            """)
    Page<Egreso> buscarPaginado(@Param("texto") String texto,
                                @Param("desde") LocalDate desde,
                                @Param("hasta") LocalDate hasta,
                                Pageable pageable);

    /**
     * Suma de TODOS los egresos que cumplen los filtros, no solo los de la
     * página actual. Se necesita porque el total al pie de la tabla debe
     * reflejar el filtro completo, no los 20 que se están viendo.
     * Devuelve null si no hay ninguno.
     */
    @Query("""
            SELECT SUM(e.monto) FROM Egreso e
            WHERE e.activo = true
              AND (:desde IS NULL OR e.fechaEgreso >= :desde)
              AND (:hasta IS NULL OR e.fechaEgreso <= :hasta)
              AND (:texto IS NULL
                   OR LOWER(e.concepto) LIKE LOWER(CONCAT('%', :texto, '%'))
                   OR LOWER(COALESCE(e.comentarios, '')) LIKE LOWER(CONCAT('%', :texto, '%'))
                   OR LOWER(e.usuario.nombreUsuario) LIKE LOWER(CONCAT('%', :texto, '%')))
            """)
    java.math.BigDecimal sumarConFiltros(@Param("texto") String texto,
                                         @Param("desde") LocalDate desde,
                                         @Param("hasta") LocalDate hasta);
}