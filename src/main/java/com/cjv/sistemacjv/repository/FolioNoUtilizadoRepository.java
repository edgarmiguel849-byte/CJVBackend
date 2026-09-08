package com.cjv.sistemacjv.repository;

import com.cjv.sistemacjv.entity.FolioNoUtilizado;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface FolioNoUtilizadoRepository extends JpaRepository<FolioNoUtilizado, Integer> {

    /**
     * Busca por TIPO + FOLIO, nunca por folio solo.
     *
     * La restricción UNIQUE de la base es sobre las dos columnas juntas,
     * así que esto sí puede devolver Optional sin riesgo de reventar por
     * resultado no único.
     */
    Optional<FolioNoUtilizado> findByTipoAndFolio(String tipo, String folio);

    /**
     * Todos los folios muertos de un tipo, ordenados alfabéticamente y de
     * menor a mayor.
     *
     * Ordenar el TEXTO alcanza porque el formato es letra + 4 dígitos de
     * ancho fijo: C2378 va antes que C9992, y C antes que F. Si algún día
     * entraran folios de otro largo, este orden se rompería en silencio.
     */
    List<FolioNoUtilizado> findByTipoOrderByFolioAsc(String tipo);

    /** Lo mismo, acotado a un periodo. Las dos fechas son inclusivas. */
    @Query("""
           SELECT f FROM FolioNoUtilizado f
           WHERE f.tipo = :tipo
             AND (:desde IS NULL OR f.fecha >= :desde)
             AND (:hasta IS NULL OR f.fecha <= :hasta)
           ORDER BY f.folio ASC
           """)
    List<FolioNoUtilizado> buscarPorTipoYRango(@Param("tipo") String tipo,
                                               @Param("desde") LocalDate desde,
                                               @Param("hasta") LocalDate hasta);
}