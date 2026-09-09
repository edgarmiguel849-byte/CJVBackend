package com.cjv.sistemacjv.repository;

import com.cjv.sistemacjv.entity.CierreCajaDetalle;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CierreCajaDetalleRepository extends JpaRepository<CierreCajaDetalle, Integer> {

    /**
     * Los renglones de un corte: qué movimientos se llevó.
     * Se usa para mostrar el detalle y para borrarlos cuando el corte se
     * deshace.
     */
    List<CierreCajaDetalle> findByCierreCaja_IdCierreCaja(Integer idCierreCaja);

    /**
     * Borra todos los renglones de un corte.
     *
     * ES OBLIGATORIO llamarlo ANTES de borrar el corte mismo. La llave
     * foránea fk_detalle_cierre_caja es ON DELETE RESTRICT: MySQL se niega
     * a borrar un cierre que todavía tenga renglones colgando, y el error
     * que sale es de llave foránea, ilegible para quien lo vea en pantalla.
     *
     * También se usa al REENVIAR un corte reabierto: los renglones viejos
     * se tiran y se vuelven a escribir con lo que quedó después de las
     * correcciones. Si no se tiraran, un movimiento borrado durante la
     * corrección seguiría marcado como cortado para siempre.
     */
    void deleteByCierreCaja_IdCierreCaja(Integer idCierreCaja);
}