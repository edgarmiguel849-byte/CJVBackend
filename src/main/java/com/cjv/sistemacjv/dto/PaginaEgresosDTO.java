package com.cjv.sistemacjv.dto;

import com.cjv.sistemacjv.entity.Egreso;
import org.springframework.data.domain.Page;

import java.math.BigDecimal;

/**
 * Respuesta de la pantalla de Egresos: la página que se va a mostrar
 * más la suma de TODOS los egresos que cumplen el filtro.
 *
 * Se necesitan los dos juntos porque el total al pie de la tabla debe
 * reflejar el filtro completo, no solo los 20 registros visibles.
 */
public class PaginaEgresosDTO {

    private Page<Egreso> pagina;
    private BigDecimal totalMonto;

    public PaginaEgresosDTO(Page<Egreso> pagina, BigDecimal totalMonto) {
        this.pagina = pagina;
        this.totalMonto = totalMonto;
    }

    public Page<Egreso> getPagina() {
        return pagina;
    }

    public void setPagina(Page<Egreso> pagina) {
        this.pagina = pagina;
    }

    public BigDecimal getTotalMonto() {
        return totalMonto;
    }

    public void setTotalMonto(BigDecimal totalMonto) {
        this.totalMonto = totalMonto;
    }
}