package com.cjv.sistemacjv.service;

import com.cjv.sistemacjv.entity.Vendedor;
import com.cjv.sistemacjv.repository.VendedorRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Service
public class VendedorService {

    private final VendedorRepository vendedorRepository;

    public VendedorService(VendedorRepository vendedorRepository) {
        this.vendedorRepository = vendedorRepository;
    }

    // Todos (para administrarlos)
    public List<Vendedor> listarTodos() {
        return vendedorRepository.findAll();
    }

    // Solo activos (para el desplegable de O.T.)
    public List<Vendedor> listarActivos() {
        return vendedorRepository.findByActivoTrueOrderByNombreAsc();
    }

    public Optional<Vendedor> buscarPorId(Integer id) {
        return vendedorRepository.findById(id);
    }

    public Vendedor crear(Vendedor vendedor) {
        validar(vendedor);
        if (vendedor.getActivo() == null) {
            vendedor.setActivo(true);
        }
        return vendedorRepository.save(vendedor);
    }

    public Optional<Vendedor> actualizar(Integer id, Vendedor datos) {
        return vendedorRepository.findById(id).map(v -> {
            validar(datos);
            v.setNombre(datos.getNombre());
            v.setPorcentajeComision(datos.getPorcentajeComision());
            if (datos.getActivo() != null) {
                v.setActivo(datos.getActivo());
            }
            return vendedorRepository.save(v);
        });
    }

    // Baja lógica: no se borra, se marca inactivo (para no romper O.T. viejas)
    public boolean desactivar(Integer id) {
        return vendedorRepository.findById(id).map(v -> {
            v.setActivo(false);
            vendedorRepository.save(v);
            return true;
        }).orElse(false);
    }

    private void validar(Vendedor v) {
        if (v.getNombre() == null || v.getNombre().trim().isEmpty()) {
            throw new RuntimeException("El nombre del vendedor es obligatorio.");
        }
        BigDecimal p = v.getPorcentajeComision();
        if (p == null || p.compareTo(BigDecimal.ZERO) < 0 || p.compareTo(new BigDecimal("100")) > 0) {
            throw new RuntimeException("El porcentaje de comisión debe estar entre 0 y 100.");
        }
    }
}