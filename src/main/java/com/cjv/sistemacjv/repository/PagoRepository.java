package com.cjv.sistemacjv.repository;

import com.cjv.sistemacjv.entity.Pago;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface PagoRepository extends JpaRepository<Pago, Integer> {

    List<Pago> findByContrato_IdContratoAndActivoTrue(Integer idContrato);

    // Pagos cobrados dentro de un rango de fechas (para el reporte de comisiones)
    // Pagos cobrados dentro de un rango de fechas (para el reporte de comisiones)
    List<Pago> findByActivoTrueAndFechaPagoBetween(LocalDate desde, LocalDate hasta);

    /**
     * Los pagos que UNA persona cobró en UN día.
     *
     * Es la base del corte individual: cada quien entrega el dinero que él
     * recibió. El de arriba trae el día completo de todos y ese se queda
     * para Reportes, que es del Jefe.
     *
     * El orden por id es el orden en que se capturaron, que es como se leen
     * en la hoja física.
     */
    List<Pago> findByActivoTrueAndFechaPagoAndUsuario_IdUsuarioOrderByIdPagoAsc(
            LocalDate fecha, Integer idUsuario);
    /**
     * Búsqueda paginada para la pantalla de Pagos.
     * Los tres filtros son opcionales: si llegan en null, no se aplican.
     *
     * DÓNDE BUSCA EL TEXTO:
     *   - folio del recibo
     *   - folio del contrato
     *   - modo de pago
     *   - nombre del alumno guardado en el contrato (contratos NUEVOS)
     *   - nombre armado del cliente (contratos VIEJOS)
     *
     * POR QUÉ LOS "LEFT JOIN" EXPLÍCITOS:
     * Antes la consulta escribía el camino largo (p.contrato.cliente.nombre)
     * directo en el WHERE. Escrito así, Hibernate arma una unión OBLIGATORIA:
     * el pago solo aparece si su contrato tiene cliente. Como los contratos
     * nuevos ya no usan 'cliente' (guardan el nombre en 'nombreAlumno'), sus
     * pagos quedaban fuera de la lista COMPLETA, no solo de la búsqueda.
     *
     * Con LEFT JOIN la unión es opcional: si hay cliente se usa, y si no, el
     * pago aparece de todos modos.
     *
     * El countQuery va escrito a mano a propósito: es el que cuenta cuántos
     * resultados hay en total para armar la paginación, y así se garantiza
     * que cuente exactamente lo mismo que trae la consulta de arriba.
     */
    @Query(value = """
            SELECT p FROM Pago p
            LEFT JOIN p.contrato c
            LEFT JOIN c.cliente cl
            WHERE p.activo = true
              AND (:desde IS NULL OR p.fechaPago >= :desde)
              AND (:hasta IS NULL OR p.fechaPago <= :hasta)
              AND (:texto IS NULL
                   OR LOWER(p.folio) LIKE LOWER(CONCAT('%', :texto, '%'))
                   OR LOWER(c.folio) LIKE LOWER(CONCAT('%', :texto, '%'))
                   OR LOWER(p.modoPago) LIKE LOWER(CONCAT('%', :texto, '%'))
                   OR LOWER(c.nombreAlumno) LIKE LOWER(CONCAT('%', :texto, '%'))
                   OR LOWER(CONCAT(COALESCE(cl.nombre, ''), ' ',
                                   COALESCE(cl.apellidoPaterno, ''), ' ',
                                   COALESCE(cl.apellidoMaterno, '')))
                      LIKE LOWER(CONCAT('%', :texto, '%')))
            """,
            countQuery = """
            SELECT COUNT(p) FROM Pago p
            LEFT JOIN p.contrato c
            LEFT JOIN c.cliente cl
            WHERE p.activo = true
              AND (:desde IS NULL OR p.fechaPago >= :desde)
              AND (:hasta IS NULL OR p.fechaPago <= :hasta)
              AND (:texto IS NULL
                   OR LOWER(p.folio) LIKE LOWER(CONCAT('%', :texto, '%'))
                   OR LOWER(c.folio) LIKE LOWER(CONCAT('%', :texto, '%'))
                   OR LOWER(p.modoPago) LIKE LOWER(CONCAT('%', :texto, '%'))
                   OR LOWER(c.nombreAlumno) LIKE LOWER(CONCAT('%', :texto, '%'))
                   OR LOWER(CONCAT(COALESCE(cl.nombre, ''), ' ',
                                   COALESCE(cl.apellidoPaterno, ''), ' ',
                                   COALESCE(cl.apellidoMaterno, '')))
                      LIKE LOWER(CONCAT('%', :texto, '%')))
            """)
    Page<Pago> buscarPaginado(@Param("texto") String texto,
                              @Param("desde") LocalDate desde,
                              @Param("hasta") LocalDate hasta,
                              Pageable pageable);

    // ===================== MATRIZ DE ADICIONALES "0/AA" =====================

    /**
     * Todos los pagos activos de una lista de contratos, en un solo viaje a la base.
     *
     * Sirve para la matriz de adicionales: primero se piden los contratos del año
     * y luego, con sus IDs, se piden TODOS sus pagos de una vez. La alternativa
     * (preguntar los pagos alumno por alumno) haría cientos de consultas.
     *
     * El orden es fecha y, si dos pagos cayeron el mismo día, id_pago. Ese
     * desempate importa: sin él la base podría devolverlos en distinto orden
     * cada vez y las columnas Pago1, Pago2... cambiarían de lugar al recargar.
     *
     * Incluye las DEVOLUCIONES (vienen con monto negativo y folio serie "Z"),
     * porque en la matriz se muestran como un pago más en negativo.
     */
    @Query("""
            SELECT p FROM Pago p
            LEFT JOIN FETCH p.contrato
            WHERE p.activo = true
              AND p.contrato.idContrato IN :idsContrato
            ORDER BY p.fechaPago ASC, p.idPago ASC
            """)
    List<Pago> listarDeContratos(@Param("idsContrato") List<Integer> idsContrato);
    /** Un pago por su folio. Devuelve lista para no reventar si hubiera dos. */
    List<Pago> findByFolio(String folio);

    /**
     * Los recibos que SÍ se usaron, para el reporte de control de folios.
     * Solo los activos y con folio capturado.
     */
    @Query("""
           SELECT p FROM Pago p
           WHERE p.activo = true
             AND p.folio IS NOT NULL AND p.folio <> ''
             AND (:desde IS NULL OR p.fechaPago >= :desde)
             AND (:hasta IS NULL OR p.fechaPago <= :hasta)
           ORDER BY p.folio ASC
           """)
    List<Pago> listarFoliosUsados(@Param("desde") LocalDate desde,
                                  @Param("hasta") LocalDate hasta);
}