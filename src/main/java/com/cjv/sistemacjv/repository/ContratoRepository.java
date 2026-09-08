package com.cjv.sistemacjv.repository;

import com.cjv.sistemacjv.entity.Contrato;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface ContratoRepository extends JpaRepository<Contrato, Integer> {

    // Para validar que el folio no se repita (F-7123)
    Optional<Contrato> findByFolio(String folio);

    // Contratos de una O.T., ordenados por su número de lista (columna ORDEN del formato)
    List<Contrato> findByOrdenTrabajo_IdOrdenTrabajoOrderByNumeroListaAsc(Integer idOrdenTrabajo);

    // Contratos firmados dentro de un rango de fechas (para comisionar sus anticipos)
    List<Contrato> findByActivoTrueAndFechaContratoBetween(LocalDate desde, LocalDate hasta);

    // Devuelve el numero_lista más alto usado en esa O.T.
    // Sirve para asignar el siguiente consecutivo automáticamente.
    // Regresa null si la O.T. todavía no tiene contratos.
    @Query("SELECT MAX(c.numeroLista) FROM Contrato c " +
            "WHERE c.ordenTrabajo.idOrdenTrabajo = :idOrdenTrabajo")
    Integer obtenerMaximoNumeroLista(@Param("idOrdenTrabajo") Integer idOrdenTrabajo);

    // Buscador de alumno: el nombre ahora vive en el propio contrato
    // (columna nombre_alumno), ya no en la tabla cliente. Coincide por
    // nombre del alumno o por folio del contrato. Solo contratos activos.
    @Query("SELECT c FROM Contrato c " +
            "WHERE c.activo = true AND ( " +
            "  LOWER(c.nombreAlumno) LIKE LOWER(CONCAT('%', :texto, '%')) OR " +
            "  LOWER(c.folio) LIKE LOWER(CONCAT('%', :texto, '%')) " +
            ") ORDER BY c.nombreAlumno ASC")
    List<Contrato> buscarAlumno(@Param("texto") String texto);

    /**
     * Búsqueda paginada para la pantalla de Contratos.
     * Los tres filtros son opcionales: si llegan en null, no se aplican.
     * El texto se compara contra folio del contrato, nombre del alumno,
     * estado, carrera (texto de la O.T.) y número de O.T.
     */
    @Query("""
            SELECT c FROM Contrato c
            LEFT JOIN c.ordenTrabajo ot
            WHERE c.ordenTrabajo IS NOT NULL
              AND (:desde IS NULL OR c.fechaContrato >= :desde)
              AND (:hasta IS NULL OR c.fechaContrato <= :hasta)
              AND (:texto IS NULL
                   OR LOWER(c.folio) LIKE LOWER(CONCAT('%', :texto, '%'))
                   OR LOWER(c.estadoContrato.nombreEstado) LIKE LOWER(CONCAT('%', :texto, '%'))
                   OR LOWER(ot.carreraTexto) LIKE LOWER(CONCAT('%', :texto, '%'))
                   OR CONCAT(ot.numero, '') LIKE CONCAT('%', :texto, '%')
                   OR LOWER(c.nombreAlumno) LIKE LOWER(CONCAT('%', :texto, '%'))
                   OR LOWER(c.ad) LIKE LOWER(CONCAT('%', :texto, '%')))
            """)
    Page<Contrato> buscarPaginado(@Param("texto") String texto,
                                  @Param("desde") LocalDate desde,
                                  @Param("hasta") LocalDate hasta,
                                  Pageable pageable);

    /**
     * Igual que buscarPaginado pero SOLO para adicionales: contratos SIN O.T.
     * (los adicionales de hoy no tienen orden de trabajo). El texto busca por
     * folio, estado, nombre del alumno, 'ad', y la carrera/escuela propias del
     * adicional (que en grupo vivirían en la O.T., pero aquí van en el contrato).
     */
    @Query("""
            SELECT c FROM Contrato c
            WHERE c.ordenTrabajo IS NULL
              AND (c.activo IS NULL OR c.activo = true)
              AND (:desde IS NULL OR c.fechaContrato >= :desde)
              AND (:hasta IS NULL OR c.fechaContrato <= :hasta)
              AND (:texto IS NULL
                   OR LOWER(c.folio) LIKE LOWER(CONCAT('%', :texto, '%'))
                   OR LOWER(c.estadoContrato.nombreEstado) LIKE LOWER(CONCAT('%', :texto, '%'))
                   OR LOWER(c.nombreAlumno) LIKE LOWER(CONCAT('%', :texto, '%'))
                   OR LOWER(c.ad) LIKE LOWER(CONCAT('%', :texto, '%'))
                   OR LOWER(c.carrera) LIKE LOWER(CONCAT('%', :texto, '%'))
                   OR LOWER(c.escuela) LIKE LOWER(CONCAT('%', :texto, '%')))
            """)
    Page<Contrato> buscarPaginadoAdicionales(@Param("texto") String texto,
                                             @Param("desde") LocalDate desde,
                                             @Param("hasta") LocalDate hasta,
                                             Pageable pageable);

    // ===================== MATRIZ DE ADICIONALES "0/AA" =====================
    // Las dos consultas de abajo son SOLO DE LECTURA y alimentan la matriz
    // que replica el Excel de adicionales. No crean ninguna O.T. real:
    // el renglón "0/26" es nada más una vista por año.

    /**
     * ¿De qué años tengo adicionales?
     *
     * Devuelve la lista de años (sacados de la fecha del contrato) que tienen
     * al menos un adicional, del más nuevo al más viejo: [2026, 2025, 2024...].
     * De aquí salen los renglones fijos "O.T. 0/26", "O.T. 0/25", etc.
     * Se acumulan solos: cuando entre el primer adicional de 2027, el 0/27
     * aparece sin que nadie tenga que crearlo a mano.
     *
     * Mismo criterio de siempre: sin O.T. y activo (los traspasados quedan fuera).
     */
    @Query("""
            SELECT DISTINCT YEAR(c.fechaContrato) FROM Contrato c
            WHERE c.ordenTrabajo IS NULL
              AND (c.activo IS NULL OR c.activo = true)
            ORDER BY YEAR(c.fechaContrato) DESC
            """)
    List<Integer> aniosConAdicionales();

    /**
     * Todos los adicionales de un año, para armar la matriz de ese año.
     *
     * Ordenados por el número de adicional (el campo 'ad': A0001, A0002...),
     * que es el mismo orden del Excel. Si algún adicional no tiene 'ad'
     * capturado, MySQL lo acomoda hasta arriba; el consecutivo "#" de la
     * matriz se calcula después, ya con la lista en la mano.
     *
     * Incluye los CANCELADOS a propósito (para pintarlos en rojo, igual que
     * en el Excel) y excluye los que se dieron de baja por traspaso.
     */
    @Query("""
            SELECT c FROM Contrato c
            WHERE c.ordenTrabajo IS NULL
              AND (c.activo IS NULL OR c.activo = true)
              AND YEAR(c.fechaContrato) = :anio
            ORDER BY c.ad ASC, c.fechaContrato ASC, c.idContrato ASC
            """)
    List<Contrato> listarAdicionalesDelAnio(@Param("anio") Integer anio);
    /**
     * Folios usados en contratos de GRUPO (los que tienen O.T.), para el
     * reporte de control. Los adicionales van en el método de abajo: son
     * talonarios distintos aunque compartan tabla.
     */
    @Query("""
           SELECT c FROM Contrato c
           WHERE c.folio IS NOT NULL AND c.folio <> ''
             AND c.ordenTrabajo IS NOT NULL
             AND (:desde IS NULL OR c.fechaContrato >= :desde)
             AND (:hasta IS NULL OR c.fechaContrato <= :hasta)
           ORDER BY c.folio ASC
           """)
    List<Contrato> listarFoliosDeGrupo(@Param("desde") LocalDate desde,
                                       @Param("hasta") LocalDate hasta);

    /** Lo mismo pero para ADICIONALES: contratos SIN O.T. */
    @Query("""
           SELECT c FROM Contrato c
           WHERE c.folio IS NOT NULL AND c.folio <> ''
             AND c.ordenTrabajo IS NULL
             AND (:desde IS NULL OR c.fechaContrato >= :desde)
             AND (:hasta IS NULL OR c.fechaContrato <= :hasta)
           ORDER BY c.folio ASC
           """)
    List<Contrato> listarFoliosDeAdicionales(@Param("desde") LocalDate desde,
                                             @Param("hasta") LocalDate hasta);
}