package com.cjv.sistemacjv.repository;

import com.cjv.sistemacjv.entity.CierreCaja;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
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
     * Los cortes que UNA persona hizo en UN día.
     *
     * Es la consulta de "¿ya entregué el mío?". El Mostrador tendrá cuando
     * mucho uno; el Administrativo puede tener varios.
     *
     * Lista vacía = esa persona todavía no corta ese día.
     */
    List<CierreCaja> findAllByFechaCierreAndUsuario_IdUsuarioOrderByIdCierreCajaAsc(
            LocalDate fechaCierre, Integer idUsuario);

    /**
     * Cortes de un rango de fechas, para pintar el calendario del Jefe.
     * Trae TODOS los del rango: el color de cada día se decide en el
     * servicio mirando los estados de ese día juntos.
     */
    List<CierreCaja> findAllByFechaCierreBetweenOrderByFechaCierreAsc(
            LocalDate desde, LocalDate hasta);

    /**
     * ¿Este PAGO ya está dentro de un corte que traba?
     *
     * Esta es la mitad nueva del candado. Antes se preguntaba por la FECHA
     * del movimiento; ahora se pregunta por el movimiento mismo, porque un
     * día puede tener el corte de Tete entregado y el de Adri abierto.
     *
     * Traba = el corte NO está REABIERTO. Un corte devuelto para corregir
     * suelta sus movimientos, que es justo para lo que sirve reabrirlo.
     *
     * Los renglones con activo = 0 no cuentan: son de un corte deshecho.
     */
    @Query("""
           SELECT COUNT(d) > 0
           FROM CierreCajaDetalle d
           WHERE d.pago.idPago = :idPago
             AND d.activo = true
             AND d.cierreCaja.estado <> 'REABIERTO'
           """)
    boolean pagoEstaEnCorteQueTraba(@Param("idPago") Integer idPago);

    /**
     * Lo mismo que arriba, pero para el ANTICIPO de un contrato.
     * El anticipo es dinero que entró al cajón el día que se firmó, así
     * que entra al corte igual que un pago.
     */
    @Query("""
           SELECT COUNT(d) > 0
           FROM CierreCajaDetalle d
           WHERE d.contrato.idContrato = :idContrato
             AND d.activo = true
             AND d.cierreCaja.estado <> 'REABIERTO'
           """)
    boolean contratoEstaEnCorteQueTraba(@Param("idContrato") Integer idContrato);

    /** Lo mismo, para un egreso. */
    @Query("""
           SELECT COUNT(d) > 0
           FROM CierreCajaDetalle d
           WHERE d.egreso.idEgreso = :idEgreso
             AND d.activo = true
             AND d.cierreCaja.estado <> 'REABIERTO'
           """)
    boolean egresoEstaEnCorteQueTraba(@Param("idEgreso") Integer idEgreso);

    /**
     * OJO - NO USAR EN CODIGO NUEVO. Se va a borrar.
     *
     * Esta consulta asume que un día tiene CUANDO MUCHO un cierre. En cuanto
     * exista un segundo corte para la misma fecha, truena con
     * IncorrectResultSizeDataAccessException y se lleva entre las patas el
     * registro de pagos, contratos y egresos de ese día.
     *
     * Sigue aquí solo para que compile mientras se migran sus dos usos
     * restantes (los dos viven en CierreCajaService).
     */
    @Deprecated
    Optional<CierreCaja> findByFechaCierre(LocalDate fechaCierre);

    /**
     * Historial de cierres, del más reciente al más viejo.
     */
    List<CierreCaja> findAllByOrderByFechaCierreDesc();
}