package com.cjv.sistemacjv.entity;

import jakarta.persistence.*;

/**
 * Un renglón del corte: dice QUÉ movimiento se llevó ese corte.
 *
 * Existe para responder una sola pregunta: "¿este dinero ya se cortó?".
 * Sin esta tabla, el segundo corte del día volvería a barrer lo que el
 * primero ya se había llevado, y el dinero se contaría dos veces.
 *
 * Un renglón apunta a UNO SOLO de los tres tipos de movimiento que entran
 * al corte; los otros dos van en null:
 *
 *   pago     -> un abono o una devolución
 *   contrato -> el ANTICIPO de ese contrato (no el contrato entero)
 *   egreso   -> una salida de dinero
 *
 * Los tres son opcionales a nivel de columna porque la base no puede
 * expresar "exactamente uno de estos tres". Esa regla la aplica
 * CierreCajaService al armar el corte, y por eso existe esTipoValido():
 * para que quede escrita en un solo lugar y se pueda revisar antes de
 * guardar.
 */
@Entity
@Table(name = "cierre_caja_detalle")
public class CierreCajaDetalle {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_cierre_caja_detalle")
    private Integer idCierreCajaDetalle;

    @ManyToOne
    @JoinColumn(name = "id_cierre_caja", nullable = false)
    private CierreCaja cierreCaja;

    /**
     * OJO: ya NO es nullable=false.
     *
     * Antes esta tabla solo guardaba pagos. Ahora un renglón puede ser de
     * un contrato o de un egreso, y en esos casos aquí va null. La columna
     * de la base se cambió a NULL para que esto sea posible.
     */
    @ManyToOne
    @JoinColumn(name = "id_contrato")
    private Contrato contrato;

    @ManyToOne
    @JoinColumn(name = "id_pago")
    private Pago pago;

    @ManyToOne
    @JoinColumn(name = "id_egreso")
    private Egreso egreso;

    @Column(name = "activo")
    private Boolean activo = true;

    public CierreCajaDetalle() {
    }

    /**
     * Un renglón es válido si apunta a EXACTAMENTE uno de los tres.
     *
     * Cero significa un renglón fantasma que no representa dinero alguno.
     * Dos o más haría que el mismo renglón se contara varias veces, o peor,
     * que un movimiento quedara marcado como cortado sin estarlo.
     */
    public boolean esTipoValido() {
        int cuantos = 0;
        if (pago != null) cuantos++;
        if (contrato != null) cuantos++;
        if (egreso != null) cuantos++;
        return cuantos == 1;
    }

    public Integer getIdCierreCajaDetalle() {
        return idCierreCajaDetalle;
    }

    public void setIdCierreCajaDetalle(Integer idCierreCajaDetalle) {
        this.idCierreCajaDetalle = idCierreCajaDetalle;
    }

    public CierreCaja getCierreCaja() {
        return cierreCaja;
    }

    public void setCierreCaja(CierreCaja cierreCaja) {
        this.cierreCaja = cierreCaja;
    }

    public Contrato getContrato() {
        return contrato;
    }

    public void setContrato(Contrato contrato) {
        this.contrato = contrato;
    }

    public Pago getPago() {
        return pago;
    }

    public void setPago(Pago pago) {
        this.pago = pago;
    }

    public Egreso getEgreso() {
        return egreso;
    }

    public void setEgreso(Egreso egreso) {
        this.egreso = egreso;
    }

    public Boolean getActivo() {
        return activo;
    }

    public void setActivo(Boolean activo) {
        this.activo = activo;
    }
}