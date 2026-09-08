package com.cjv.sistemacjv.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Un renglón de la matriz de adicionales = un contrato sin O.T.
 *
 * Réplica del Excel: # · CONTRATO · N° ADICIONAL · NOMBRE(S) ·
 * CARRERA · ESCUELA · GENERACION · CONCEPTO · RECIBOS ANT. · ANTICIPO ·
 * FOLIO+PAGO1 ... FOLIO+PAGOn · TOTAL · ABONO · RESTA · %
 *
 * Carrera y escuela van en columnas separadas (en el Excel venían
 * pegadas en una sola celda, pero en la base son dos campos).
 *
 * La lista 'pagos' SIEMPRE trae el mismo tamaño que las columnas de
 * la matriz. Las celdas que este alumno no usó llegan en null, para
 * que el frontend las pinte vacías sin tener que contar nada.
 */
public class FilaAdicionalDTO {

    private Integer numero;          // el consecutivo "#" de la hoja (1, 2, 3...)
    private Integer idContrato;      // para poder abrir el contrato con un clic
    private String folioContrato;
    private String ad;               // N° de adicional (A0001, A0002...)
    private String nombreAlumno;
    private String carrera;
    private String escuela;
    private String generacion;
    private String concepto;         // las observaciones del contrato
    private String estado;           // "Activo" / "Cancelado"
    private LocalDate fechaEntrega;  // desde aquí corre el reloj del vencimiento

    /**
     * Global de Recibos Físicos Anteriores: lo que el alumno ya había
     * pagado en su contrato viejo cuando se recontrató.
     *
     * Va en columna APARTE del anticipo a propósito. Antes los dos números
     * venían sumados en una sola casilla y no se podía saber cuál era cuál;
     * ahora cada cantidad aparece UNA vez y en su lugar.
     *
     * Este dinero NO entró a la caja el día del contrato nuevo: se cobró y
     * se comisionó en el anterior.
     */
    private BigDecimal recibosAnteriores;

    /** Solo el anticipo del propio contrato: lo que sí entró a la caja ese día. */
    private BigDecimal anticipo;

    private List<CeldaPagoDTO> pagos;

    private BigDecimal total;
    private BigDecimal abonado;      // recibos anteriores + anticipo + todos los pagos
    private BigDecimal resta;        // total - abonado (negativo = pagó de más)
    private BigDecimal porcentaje;   // abonado / total (1 = pagado completo)

    private boolean cancelado;

    /**
     * Etiqueta informativa: pasaron 4 meses desde la fecha de entrega y
     * todavía debe dinero. NO es un estado de la base; el contrato sigue
     * "Activo" y funciona normal. Se calcula aquí para que la regla viva
     * en un solo lugar y no repetida en cada pantalla.
     */
    private boolean vencido;

    public FilaAdicionalDTO(Integer numero, Integer idContrato, String folioContrato,
                            String ad, String nombreAlumno, String carrera, String escuela,
                            String generacion, String concepto, String estado,
                            LocalDate fechaEntrega, BigDecimal recibosAnteriores,
                            BigDecimal anticipo,
                            List<CeldaPagoDTO> pagos, BigDecimal total, BigDecimal abonado,
                            BigDecimal resta, BigDecimal porcentaje,
                            boolean cancelado, boolean vencido) {
        this.numero = numero;
        this.idContrato = idContrato;
        this.folioContrato = folioContrato;
        this.ad = ad;
        this.nombreAlumno = nombreAlumno;
        this.carrera = carrera;
        this.escuela = escuela;
        this.generacion = generacion;
        this.concepto = concepto;
        this.estado = estado;
        this.fechaEntrega = fechaEntrega;
        this.recibosAnteriores = recibosAnteriores;
        this.anticipo = anticipo;
        this.pagos = pagos;
        this.total = total;
        this.abonado = abonado;
        this.resta = resta;
        this.porcentaje = porcentaje;
        this.cancelado = cancelado;
        this.vencido = vencido;
    }

    public Integer getNumero() { return numero; }
    public Integer getIdContrato() { return idContrato; }
    public String getFolioContrato() { return folioContrato; }
    public String getAd() { return ad; }
    public String getNombreAlumno() { return nombreAlumno; }
    public String getCarrera() { return carrera; }
    public String getEscuela() { return escuela; }
    public String getGeneracion() { return generacion; }
    public String getConcepto() { return concepto; }
    public String getEstado() { return estado; }
    public LocalDate getFechaEntrega() { return fechaEntrega; }
    public BigDecimal getRecibosAnteriores() { return recibosAnteriores; }
    public BigDecimal getAnticipo() { return anticipo; }
    public List<CeldaPagoDTO> getPagos() { return pagos; }
    public BigDecimal getTotal() { return total; }
    public BigDecimal getAbonado() { return abonado; }
    public BigDecimal getResta() { return resta; }
    public BigDecimal getPorcentaje() { return porcentaje; }
    public boolean isCancelado() { return cancelado; }
    public boolean isVencido() { return vencido; }
}