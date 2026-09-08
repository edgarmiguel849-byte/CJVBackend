package com.cjv.sistemacjv.entity;

import jakarta.persistence.*;

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

    @ManyToOne
    @JoinColumn(name = "id_pago", nullable = false)
    private Pago pago;

    @Column(name = "activo")
    private Boolean activo = true;

    public CierreCajaDetalle() {
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

    public Pago getPago() {
        return pago;
    }

    public void setPago(Pago pago) {
        this.pago = pago;
    }

    public Boolean getActivo() {
        return activo;
    }

    public void setActivo(Boolean activo) {
        this.activo = activo;
    }
}