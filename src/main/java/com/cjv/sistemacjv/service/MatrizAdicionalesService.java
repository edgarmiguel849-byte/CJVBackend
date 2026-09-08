package com.cjv.sistemacjv.service;

import com.cjv.sistemacjv.dto.CeldaPagoDTO;
import com.cjv.sistemacjv.dto.FilaAdicionalDTO;
import com.cjv.sistemacjv.dto.MatrizAdicionalesDTO;
import com.cjv.sistemacjv.entity.Contrato;
import com.cjv.sistemacjv.entity.Pago;
import com.cjv.sistemacjv.repository.ContratoRepository;
import com.cjv.sistemacjv.repository.PagoRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Arma la matriz de adicionales "0/AA": la réplica del Excel dentro del sistema.
 *
 * IMPORTANTE: aquí NO se crea ninguna O.T. real ni se modifica ningún dato.
 * Los adicionales siguen siendo contratos SIN orden de trabajo. Este servicio
 * solo LEE y acomoda; no escribe absolutamente nada en la base.
 */
@Service
@Transactional(readOnly = true)
public class MatrizAdicionalesService {

    /** El Excel trae 7 columnas de pago; nunca mostramos menos que eso. */
    private static final int COLUMNAS_MINIMAS = 7;

    /** Meses después de la ENTREGA para considerar vencido un adicional. */
    private static final int MESES_PARA_VENCER = 4;

    private final ContratoRepository contratoRepository;
    private final PagoRepository pagoRepository;

    public MatrizAdicionalesService(ContratoRepository contratoRepository,
                                    PagoRepository pagoRepository) {
        this.contratoRepository = contratoRepository;
        this.pagoRepository = pagoRepository;
    }

    /**
     * Un año que tiene adicionales, ya con su etiqueta lista para pintar.
     * Se manda como clase anidada para no llenar la carpeta dto de cajitas
     * de dos campos (mismo estilo que ModoPantallaContratos).
     */
    public static class AnioAdicionales {
        private final Integer anio;
        private final String etiqueta;

        public AnioAdicionales(Integer anio, String etiqueta) {
            this.anio = anio;
            this.etiqueta = etiqueta;
        }

        public Integer getAnio() { return anio; }
        public String getEtiqueta() { return etiqueta; }
    }

    /**
     * ¿Qué años tienen adicionales? Devuelve [{2026, "0/26"}, {2025, "0/25"}...]
     * del más nuevo al más viejo. De aquí salen los renglones fijos que van
     * arriba en la lista de Órdenes de Trabajo.
     */
    public List<AnioAdicionales> listarAnios() {
        List<AnioAdicionales> resultado = new ArrayList<>();
        for (Integer anio : contratoRepository.aniosConAdicionales()) {
            if (anio != null) {
                resultado.add(new AnioAdicionales(anio, etiquetaDe(anio)));
            }
        }
        return resultado;
    }

    /** 2026 -> "0/26" */
    private String etiquetaDe(Integer anio) {
        return String.format("0/%02d", anio % 100);
    }

    /**
     * La matriz completa de un año.
     *
     * Nota sobre el reparto de pagos: los pagos vienen ordenados por fecha
     * (y por id, para desempatar los del mismo día). El más viejo cae en
     * PAGO1, el siguiente en PAGO2, y así. Las devoluciones cuentan como
     * un pago más: ya vienen con monto negativo desde la base.
     */
    public MatrizAdicionalesDTO obtenerMatriz(Integer anio) {

        List<Contrato> contratos = contratoRepository.listarAdicionalesDelAnio(anio);

        // Año sin adicionales: se devuelve la hoja vacía, no un error.
        if (contratos.isEmpty()) {
            return new MatrizAdicionalesDTO(
                    anio, etiquetaDe(anio), COLUMNAS_MINIMAS,
                    List.of(), BigDecimal.ZERO, BigDecimal.ZERO, ceros(COLUMNAS_MINIMAS),
                    BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, 0, 0);
        }

        // Un solo viaje a la base por TODOS los pagos del año.
        List<Integer> ids = new ArrayList<>();
        for (Contrato c : contratos) {
            ids.add(c.getIdContrato());
        }
        List<Pago> pagos = pagoRepository.listarDeContratos(ids);

        // Se reparten los pagos por alumno, respetando el orden que ya traían.
        Map<Integer, List<Pago>> pagosPorContrato = new LinkedHashMap<>();
        for (Pago p : pagos) {
            if (p.getContrato() != null) {
                pagosPorContrato
                        .computeIfAbsent(p.getContrato().getIdContrato(), k -> new ArrayList<>())
                        .add(p);
            }
        }

        // ¿Cuántas columnas lleva la hoja? Las del que más pagos hizo (mínimo 7).
        int columnas = COLUMNAS_MINIMAS;
        for (List<Pago> lista : pagosPorContrato.values()) {
            if (lista.size() > columnas) {
                columnas = lista.size();
            }
        }

        List<FilaAdicionalDTO> filas = new ArrayList<>();
        List<BigDecimal> totalesPorColumna = ceros(columnas);

        BigDecimal totalRecibosAnteriores = BigDecimal.ZERO;
        BigDecimal totalAnticipos = BigDecimal.ZERO;
        BigDecimal totalGeneral = BigDecimal.ZERO;
        BigDecimal abonadoGeneral = BigDecimal.ZERO;

        int numero = 0;
        int cancelados = 0;

        for (Contrato c : contratos) {
            numero++;

            // ===== LAS DOS CANTIDADES, CADA UNA EN SU COLUMNA =====
            //
            // Antes esta parte decía 'anticipo = c.getAbonadoInicial()', que
            // es anticipo + heredado sumados. El número cuadraba, pero en la
            // hoja no se podía distinguir el dinero que entró ese día del que
            // venía arrastrado de un contrato anterior.
            //
            // Ahora cada uno se lee de su propio campo. La suma es idéntica,
            // así que el ABONO y la RESTA no se mueven ni un peso: lo único
            // que cambia es que la cantidad se ve en dos casillas.
            BigDecimal recibosAnteriores = nvl(c.getAbonoHeredado());
            BigDecimal anticipo = nvl(c.getAnticipo());
            BigDecimal total = nvl(c.getTotal());

            // Las celdas FOLIO+PAGO de este alumno, rellenadas con null
            // hasta completar el ancho de la hoja.
            List<Pago> susPagos = pagosPorContrato.getOrDefault(c.getIdContrato(), List.of());
            List<CeldaPagoDTO> celdas = new ArrayList<>();
            BigDecimal sumaPagos = BigDecimal.ZERO;

            for (int i = 0; i < columnas; i++) {
                if (i < susPagos.size()) {
                    Pago p = susPagos.get(i);
                    BigDecimal monto = nvl(p.getMontoPago());
                    celdas.add(new CeldaPagoDTO(
                            p.getFolio(), monto, p.getFechaPago(), p.esDevolucion()));
                    sumaPagos = sumaPagos.add(monto);
                    totalesPorColumna.set(i, totalesPorColumna.get(i).add(monto));
                } else {
                    celdas.add(null);
                }
            }

            // Mismo resultado que antes: recibos + anticipo es exactamente lo
            // que devolvía getAbonadoInicial().
            BigDecimal abonado = recibosAnteriores.add(anticipo).add(sumaPagos);
            BigDecimal resta = total.subtract(abonado);

            boolean cancelado = esCancelado(c);
            if (cancelado) {
                cancelados++;
            }

            filas.add(new FilaAdicionalDTO(
                    numero,
                    c.getIdContrato(),
                    c.getFolio(),
                    c.getAd(),
                    c.getNombreAlumno(),
                    c.getCarrera(),
                    c.getEscuela(),
                    c.getGeneracion(),
                    c.getObservaciones(),
                    c.getEstadoContrato() != null ? c.getEstadoContrato().getNombreEstado() : null,
                    c.getFechaEntrega(),
                    recibosAnteriores,
                    anticipo,
                    celdas,
                    total,
                    abonado,
                    resta,
                    porcentaje(abonado, total),
                    cancelado,
                    esVencido(c, resta, cancelado)));

            totalRecibosAnteriores = totalRecibosAnteriores.add(recibosAnteriores);
            totalAnticipos = totalAnticipos.add(anticipo);
            totalGeneral = totalGeneral.add(total);
            abonadoGeneral = abonadoGeneral.add(abonado);
        }

        return new MatrizAdicionalesDTO(
                anio,
                etiquetaDe(anio),
                columnas,
                filas,
                totalRecibosAnteriores,
                totalAnticipos,
                totalesPorColumna,
                totalGeneral,
                abonadoGeneral,
                totalGeneral.subtract(abonadoGeneral),
                filas.size(),
                cancelados);
    }

    /**
     * ¿Este adicional está vencido?
     *
     * Regla: pasaron 4 meses desde su FECHA DE ENTREGA y todavía debe dinero.
     * Sin fecha de entrega capturada, no vence nunca (sin fecha no hay reloj).
     * Un cancelado no se marca vencido: manda la decisión humana.
     *
     * OJO: esto es SOLO una etiqueta informativa. No cambia el estado del
     * contrato en la base ni afecta comisiones, entregas ni cobros.
     */
    private boolean esVencido(Contrato c, BigDecimal resta, boolean cancelado) {
        if (cancelado) {
            return false;
        }
        if (resta == null || resta.compareTo(BigDecimal.ZERO) <= 0) {
            return false;
        }
        LocalDate entrega = c.getFechaEntrega();
        if (entrega == null) {
            return false;
        }
        return LocalDate.now().isAfter(entrega.plusMonths(MESES_PARA_VENCER));
    }

    /**
     * El % del Excel: cuánto lleva pagado de lo que debe.
     * 1 = pagado completo. Más de 1 = pagó de más (aparece con resta negativa).
     * Si el total es 0 no se puede dividir: se reporta 0 o 1 según si ya abonó algo.
     */
    private BigDecimal porcentaje(BigDecimal abonado, BigDecimal total) {
        if (total == null || total.compareTo(BigDecimal.ZERO) == 0) {
            return abonado.compareTo(BigDecimal.ZERO) == 0 ? BigDecimal.ZERO : BigDecimal.ONE;
        }
        return abonado.divide(total, 4, RoundingMode.HALF_UP);
    }

    /** Cancelado = el nombre del estado CONTIENE "cancel" (regla del sistema). */
    private boolean esCancelado(Contrato c) {
        if (c.getEstadoContrato() == null || c.getEstadoContrato().getNombreEstado() == null) {
            return false;
        }
        return c.getEstadoContrato().getNombreEstado().toLowerCase().contains("cancel");
    }

    private BigDecimal nvl(BigDecimal valor) {
        return valor != null ? valor : BigDecimal.ZERO;
    }

    private List<BigDecimal> ceros(int cuantos) {
        List<BigDecimal> lista = new ArrayList<>();
        for (int i = 0; i < cuantos; i++) {
            lista.add(BigDecimal.ZERO);
        }
        return lista;
    }
}