package com.cjv.sistemacjv.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * Una fila de la matriz = un contrato (alumno) de la O.T.
 * Las celdas van alineadas con la lista de meses del DTO principal:
 * celdasPorMes.get(0) corresponde al primer mes, get(1) al segundo, etc.
 * Cada mes es una lista porque puede haber varios pagos en el mismo mes.
 */
public class FilaMatrizDTO {

    private Integer numeroLista;
    private String folioContrato;
    private String nombre;
    private String apellidoPaterno;
    private String apellidoMaterno;

    private BigDecimal anticipo;

    // Una lista de celdas por cada mes (alineada con matriz.meses)
    private List<List<CeldaMesDTO>> celdasPorMes;

    private BigDecimal total;
    private BigDecimal abonado;
    private BigDecimal resta;

    private boolean cancelado;

    /**
     * Etiqueta informativa: pasaron 4 meses desde el CIERRE DE CICLO de la
     * O.T. y el alumno todavía debe dinero.
     * <p>
     * NO es un estado de la base: el contrato sigue "Activo" y se le puede
     * cobrar, entregar y comisionar igual. Se calcula en el backend para que
     * la regla viva en un solo lugar y no repetida en cada pantalla.
     * <p>
     * (En adicionales el reloj es distinto: arranca en la fecha de entrega
     * de cada contrato, no en un cierre de grupo.)
     */
    private boolean vencido;

    /**
     * Folio del contrato NUEVO al que se recontrató este alumno.
     * Si trae algo, el renglón se pinta MORADO en la matriz: el contrato
     * sigue vivo y suma en los totales, pero ya no se le persigue la deuda
     * aquí, porque se movió a otro folio.
     */
    private String traspasadoA;

    public FilaMatrizDTO(Integer numeroLista, String folioContrato,
                         String nombre, String apellidoPaterno, String apellidoMaterno,
                         BigDecimal anticipo, List<List<CeldaMesDTO>> celdasPorMes,
                         BigDecimal total, BigDecimal abonado, BigDecimal resta,
                         boolean cancelado, boolean vencido, String traspasadoA) {
        this.numeroLista = numeroLista;
        this.folioContrato = folioContrato;
        this.nombre = nombre;
        this.apellidoPaterno = apellidoPaterno;
        this.apellidoMaterno = apellidoMaterno;
        this.anticipo = anticipo;
        this.celdasPorMes = celdasPorMes;
        this.total = total;
        this.abonado = abonado;
        this.resta = resta;
        this.cancelado = cancelado;
        this.vencido = vencido;
        this.traspasadoA = traspasadoA;
    }

    public Integer getNumeroLista() { return numeroLista; }
    public String getFolioContrato() { return folioContrato; }
    public String getNombre() { return nombre; }
    public String getApellidoPaterno() { return apellidoPaterno; }
    public String getApellidoMaterno() { return apellidoMaterno; }
    public BigDecimal getAnticipo() { return anticipo; }
    public List<List<CeldaMesDTO>> getCeldasPorMes() { return celdasPorMes; }
    public BigDecimal getTotal() { return total; }
    public BigDecimal getAbonado() { return abonado; }
    public BigDecimal getResta() { return resta; }
    public boolean isCancelado() { return cancelado; }
    public boolean isVencido() { return vencido; }
    public String getTraspasadoA() { return traspasadoA; }
}