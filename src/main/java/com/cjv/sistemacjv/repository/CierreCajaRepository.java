package com.cjv.sistemacjv.repository;

import com.cjv.sistemacjv.entity.CierreCaja;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface CierreCajaRepository extends JpaRepository<CierreCaja, Integer> {

    /**
     * Todos los cortes de un día, en el orden en que se hicieron.
     *
     * Devuelve una lista porque un día puede tener VARIOS cortes: cada
     * persona entrega el suyo, y el Administrativo puede hacer más de uno
     * cuando el volumen de folios lo pide.
     *
     * Lista vacía = ese día todavía no tiene ningún corte.
     */
    List<CierreCaja> findAllByFechaCierreOrderByIdCierreCajaAsc(LocalDate fechaCierre);

    /**
     * OJO - NO USAR EN CODIGO NUEVO. Se va a borrar.
     *
     * Esta consulta asume que un día tiene CUANDO MUCHO un cierre. En cuanto
     * exista un segundo corte para la misma fecha, truena con
     * IncorrectResultSizeDataAccessException y se lleva entre las patas el
     * registro de pagos, contratos y egresos de ese día.
     *
     * Sigue aquí solo para que compile mientras se migran sus cinco usos a
     * findAllByFechaCierreOrderByIdCierreCajaAsc.
     */
    @Deprecated
    Optional<CierreCaja> findByFechaCierre(LocalDate fechaCierre);

    /**
     * Historial de cierres, del más reciente al más viejo.
     */
    List<CierreCaja> findAllByOrderByFechaCierreDesc();
}