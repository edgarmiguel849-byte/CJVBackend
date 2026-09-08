package com.cjv.sistemacjv.service;

import com.cjv.sistemacjv.entity.Bitacora;
import com.cjv.sistemacjv.repository.BitacoraRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class BitacoraService {

    private final BitacoraRepository bitacoraRepository;

    public BitacoraService(BitacoraRepository bitacoraRepository) {
        this.bitacoraRepository = bitacoraRepository;
    }

    public List<Bitacora> listarBitacora() {
        return bitacoraRepository.findAll();
    }

    public Optional<Bitacora> buscarPorId(Integer id) {
        return bitacoraRepository.findById(id);
    }

    public Bitacora guardarBitacora(Bitacora bitacora) {
        return bitacoraRepository.save(bitacora);
    }

    public Optional<Bitacora> actualizarBitacora(Integer id, Bitacora datosBitacora) {
        return bitacoraRepository.findById(id).map(bitacora -> {
            bitacora.setTablaAfectada(datosBitacora.getTablaAfectada());
            bitacora.setIdRegistroAfectado(datosBitacora.getIdRegistroAfectado());
            bitacora.setAccion(datosBitacora.getAccion());
            bitacora.setDescripcion(datosBitacora.getDescripcion());
            bitacora.setUsuario(datosBitacora.getUsuario());
            return bitacoraRepository.save(bitacora);
        });
    }

    public boolean eliminarBitacora(Integer id) {
        if (bitacoraRepository.existsById(id)) {
            bitacoraRepository.deleteById(id);
            return true;
        }
        return false;
    }
}