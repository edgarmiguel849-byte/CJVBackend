package com.cjv.sistemacjv.repository;

import com.cjv.sistemacjv.entity.Entrega;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface EntregaRepository extends JpaRepository<Entrega, Integer> {

    /**
     * Busca la entrega de un contrato. Si viene vacío, a ese alumno
     * todavía no le entregan. La regla uq_entrega_contrato garantiza
     * que nunca haya dos.
     */
    Optional<Entrega> findByContrato_IdContrato(Integer idContrato);

    /**
     * Todas las entregas de una O.T., ordenadas por el número de lista
     * del alumno (el mismo orden del papel).
     */
    List<Entrega> findByContrato_OrdenTrabajo_IdOrdenTrabajoOrderByContrato_NumeroListaAsc(
            Integer idOrdenTrabajo);

    /**
     * Entregas que se hicieron con saldo pendiente. Es la lista que
     * le importa al jefe: quién se llevó sus cuadros debiendo y por qué.
     */
    List<Entrega> findBySaldoAlEntregarGreaterThanOrderByFechaEntregaDesc(
            java.math.BigDecimal cero);

    /** Historial completo, de la más reciente a la más vieja. */
    List<Entrega> findAllByOrderByFechaEntregaDesc();
}