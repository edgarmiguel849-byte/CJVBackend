package com.cjv.sistemacjv.repository;

import com.cjv.sistemacjv.entity.ContratoArticulo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

/**
 * Acceso a los renglones de "Artículos Adquiridos".
 *
 * Spring Data arma solo las consultas leyendo el NOMBRE del método:
 * "findByIdContratoOrderByOrdenAsc" se traduce a "búscame los que tengan
 * este id_contrato y ordénalos por la columna orden". No hay que escribir
 * el SQL a mano.
 */
public interface ContratoArticuloRepository extends JpaRepository<ContratoArticulo, Integer> {

    /** Los artículos de UN contrato, en el orden en que se capturaron. */
    List<ContratoArticulo> findByIdContratoOrderByOrdenAsc(Integer idContrato);

    /** Los artículos de VARIOS contratos de un jalón (para listas y matrices). */
    List<ContratoArticulo> findByIdContratoInOrderByIdContratoAscOrdenAsc(List<Integer> idsContrato);

    /**
     * Borra todos los artículos de un contrato.
     *
     * Se usa al EDITAR: en vez de andar comparando renglón por renglón cuál
     * cambió, cuál se borró y cuál es nuevo, se tira la lista vieja completa
     * y se vuelve a escribir la nueva. Es como rehacer la nota en limpio en
     * vez de tachonearla.
     *
     * @Modifying es obligatorio porque esta consulta ESCRIBE en la base;
     * sin esa etiqueta Spring cree que solo va a leer y truena.
     */
    @Modifying
    @Query("DELETE FROM ContratoArticulo a WHERE a.idContrato = :idContrato")
    void borrarPorContrato(@Param("idContrato") Integer idContrato);

    /** ¿Este contrato ya tiene artículos capturados? (para los contratos viejos) */
    boolean existsByIdContrato(Integer idContrato);
}