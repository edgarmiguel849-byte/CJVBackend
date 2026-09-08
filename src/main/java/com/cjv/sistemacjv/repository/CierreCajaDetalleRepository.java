package com.cjv.sistemacjv.repository;

import com.cjv.sistemacjv.entity.CierreCajaDetalle;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CierreCajaDetalleRepository extends JpaRepository<CierreCajaDetalle, Integer> {
}