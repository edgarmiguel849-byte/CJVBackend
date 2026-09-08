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
     * Busca el cierre de un día. Sirve para dos cosas:
     * saber si ese día ya se cerró, y traerlo para mostrarlo.
     * Devuelve una caja vacía si ese día todavía no tiene cierre.
     */
    Optional<CierreCaja> findByFechaCierre(LocalDate fechaCierre);

    /**
     * Historial de cierres, del más reciente al más viejo.
     */
    List<CierreCaja> findAllByOrderByFechaCierreDesc();
}