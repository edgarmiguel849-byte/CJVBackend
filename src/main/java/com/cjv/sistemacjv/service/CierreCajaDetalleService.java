package com.cjv.sistemacjv.service;

import com.cjv.sistemacjv.entity.CierreCajaDetalle;
import com.cjv.sistemacjv.repository.CierreCajaDetalleRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class CierreCajaDetalleService {

    private final CierreCajaDetalleRepository cierreCajaDetalleRepository;

    public CierreCajaDetalleService(CierreCajaDetalleRepository cierreCajaDetalleRepository) {
        this.cierreCajaDetalleRepository = cierreCajaDetalleRepository;
    }

    public List<CierreCajaDetalle> listarDetalles() {
        return cierreCajaDetalleRepository.findAll();
    }

    public Optional<CierreCajaDetalle> buscarPorId(Integer id) {
        return cierreCajaDetalleRepository.findById(id);
    }

    public CierreCajaDetalle guardarDetalle(CierreCajaDetalle cierreCajaDetalle) {
        return cierreCajaDetalleRepository.save(cierreCajaDetalle);
    }

    public Optional<CierreCajaDetalle> actualizarDetalle(Integer id, CierreCajaDetalle datosDetalle) {
        return cierreCajaDetalleRepository.findById(id).map(detalle -> {
            detalle.setCierreCaja(datosDetalle.getCierreCaja());
            detalle.setPago(datosDetalle.getPago());
            detalle.setActivo(datosDetalle.getActivo());
            return cierreCajaDetalleRepository.save(detalle);
        });
    }

    public boolean eliminarDetalle(Integer id) {
        if (cierreCajaDetalleRepository.existsById(id)) {
            cierreCajaDetalleRepository.deleteById(id);
            return true;
        }
        return false;
    }
}