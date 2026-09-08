package com.cjv.sistemacjv.service;

import com.cjv.sistemacjv.entity.Contrato;
import com.cjv.sistemacjv.entity.ContratoArticulo;
import com.cjv.sistemacjv.repository.ContratoArticuloRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Los renglones de "Artículos Adquiridos" de un contrato adicional.
 *
 * REGLA CENTRAL: el TOTAL del contrato es la SUMA de sus artículos.
 * El navegador manda la lista de renglones; el total lo calcula el
 * servidor. Si el navegador manda un total, se ignora.
 *
 * OJO: esta clase NO toca dinero de caja. El anticipo, las comisiones
 * y el corte del día siguen exactamente igual que antes. Aquí solo se
 * define CUÁNTO cuesta el contrato, no CUÁNTO se cobró.
 */
@Service
public class ContratoArticuloService {

    private final ContratoArticuloRepository articuloRepository;

    public ContratoArticuloService(ContratoArticuloRepository articuloRepository) {
        this.articuloRepository = articuloRepository;
    }

    /** Los artículos de un contrato, en el orden en que se capturaron. */
    public List<ContratoArticulo> listarPorContrato(Integer idContrato) {
        if (idContrato == null) {
            return new ArrayList<>();
        }
        return articuloRepository.findByIdContratoOrderByOrdenAsc(idContrato);
    }

    /**
     * Los artículos para ABRIR A EDITAR un contrato.
     *
     * Red de seguridad para los contratos viejos: los que se crearon antes
     * de esta pantalla (y los que siga creando el JAR del puerto 8080) no
     * tienen artículos. Si devolviéramos una lista vacía, el total se vería
     * en $0 y al guardar se borraría el monto real.
     *
     * Por eso, cuando no hay artículos, se inventa UN renglón que dice
     * "Contrato" con el total que ya tenía. No se guarda en la base: es
     * solo para que la pantalla arranque con el monto correcto y la
     * persona lo desglose si quiere.
     */
    public List<ContratoArticulo> listarParaEditar(Contrato contrato) {
        if (contrato == null || contrato.getIdContrato() == null) {
            return new ArrayList<>();
        }

        List<ContratoArticulo> guardados =
                articuloRepository.findByIdContratoOrderByOrdenAsc(contrato.getIdContrato());

        if (!guardados.isEmpty()) {
            return guardados;
        }

        BigDecimal totalViejo = contrato.getTotal() != null
                ? contrato.getTotal()
                : BigDecimal.ZERO;

        List<ContratoArticulo> reconstruido = new ArrayList<>();
        reconstruido.add(new ContratoArticulo(
                contrato.getIdContrato(), "Contrato", totalViejo, 0));
        return reconstruido;
    }

    /**
     * Revisa que la lista que mandó el navegador sirva, y devuelve el total.
     *
     * NO escribe nada en la base. Se llama ANTES de guardar el contrato,
     * porque el total tiene que estar listo desde el primer INSERT.
     */
    public BigDecimal validarYCalcularTotal(List<ContratoArticulo> articulos) {
        if (articulos == null || articulos.isEmpty()) {
            throw new IllegalArgumentException(
                    "Captura al menos un artículo. El total del contrato sale de sumarlos.");
        }

        BigDecimal total = BigDecimal.ZERO;
        int renglon = 0;

        for (ContratoArticulo a : articulos) {
            renglon++;

            if (a == null || a.getDescripcion() == null || a.getDescripcion().isBlank()) {
                throw new IllegalArgumentException(
                        "El artículo " + renglon + " no tiene descripción.");
            }
            if (a.getDescripcion().trim().length() > 200) {
                throw new IllegalArgumentException(
                        "La descripción del artículo " + renglon
                                + " es demasiado larga (máximo 200 letras).");
            }
            if (a.getMonto() == null) {
                throw new IllegalArgumentException(
                        "El artículo " + renglon + " no tiene monto.");
            }
            // Las devoluciones NO son artículos: son pagos en negativo con
            // folio de la serie Z y viven en la tabla 'pago'. Aquí un número
            // negativo siempre es un error de captura.
            if (a.getMonto().signum() < 0) {
                throw new IllegalArgumentException(
                        "El artículo " + renglon + " tiene un monto negativo. "
                                + "Las devoluciones se registran en la pantalla de Pagos.");
            }

            total = total.add(a.getMonto());
        }

        return total;
    }

    /**
     * Guarda la lista de artículos de un contrato.
     *
     * Al editar, borra los renglones viejos y escribe los nuevos: es como
     * rehacer la nota en limpio en vez de tachonearla. Devuelve el total,
     * que es lo que se le pone al contrato.
     *
     * @Transactional hace que el borrado y la escritura sean UN solo
     * movimiento: si algo falla a media lista, no se queda el contrato
     * sin artículos.
     */
    @Transactional
    public BigDecimal reemplazarArticulos(Integer idContrato, List<ContratoArticulo> articulos) {
        if (idContrato == null) {
            throw new IllegalArgumentException("Falta el contrato al guardar los artículos.");
        }

        BigDecimal total = validarYCalcularTotal(articulos);

        articuloRepository.borrarPorContrato(idContrato);

        int orden = 0;
        List<ContratoArticulo> aGuardar = new ArrayList<>();
        for (ContratoArticulo a : articulos) {
            aGuardar.add(new ContratoArticulo(
                    idContrato,
                    a.getDescripcion().trim(),
                    a.getMonto(),
                    orden++));
        }

        articuloRepository.saveAll(aGuardar);
        return total;
    }

    /** Borra los artículos de un contrato (al eliminar el contrato). */
    @Transactional
    public void borrarPorContrato(Integer idContrato) {
        if (idContrato != null) {
            articuloRepository.borrarPorContrato(idContrato);
        }
    }
}