package com.cjv.sistemacjv.repository;

import com.cjv.sistemacjv.entity.Vendedor;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface VendedorRepository extends JpaRepository<Vendedor, Integer> {

    // Para el desplegable de "Ejecutivo de ventas": solo vendedores activos,
    // ordenados por nombre.
    List<Vendedor> findByActivoTrueOrderByNombreAsc();
}