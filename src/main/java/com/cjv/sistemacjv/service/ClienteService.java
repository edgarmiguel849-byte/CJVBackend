package com.cjv.sistemacjv.service;

import com.cjv.sistemacjv.entity.Cliente;
import com.cjv.sistemacjv.repository.ClienteRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class ClienteService {

    private final ClienteRepository clienteRepository;
    private final BitacoraLogger bitacoraLogger;

    public ClienteService(ClienteRepository clienteRepository,
                          BitacoraLogger bitacoraLogger) {
        this.clienteRepository = clienteRepository;
        this.bitacoraLogger = bitacoraLogger;
    }

    public List<Cliente> listarClientes() {
        return clienteRepository.findAll();
    }

    public Optional<Cliente> buscarPorId(Integer id) {
        return clienteRepository.findById(id);
    }

    public Cliente guardarCliente(Cliente cliente) {
        Cliente guardado = clienteRepository.save(cliente);

        bitacoraLogger.registrar(
                "cliente",
                guardado.getIdCliente(),
                "CREAR",
                "Cliente #" + guardado.getIdCliente()
                        + " registrado - " + nombreCompleto(guardado)
        );

        return guardado;
    }

    public Optional<Cliente> actualizarCliente(Integer id, Cliente datosCliente) {
        return clienteRepository.findById(id).map(cliente -> {
            cliente.setCarrera(datosCliente.getCarrera());
            cliente.setNombre(datosCliente.getNombre());
            cliente.setApellidoPaterno(datosCliente.getApellidoPaterno());
            cliente.setApellidoMaterno(datosCliente.getApellidoMaterno());
            cliente.setTelefono(datosCliente.getTelefono());
            cliente.setTelefono2(datosCliente.getTelefono2());
            cliente.setCorreo(datosCliente.getCorreo());
            cliente.setActivo(datosCliente.getActivo());
            Cliente actualizado = clienteRepository.save(cliente);

            bitacoraLogger.registrar(
                    "cliente",
                    actualizado.getIdCliente(),
                    "EDITAR",
                    "Cliente #" + actualizado.getIdCliente()
                            + " editado - " + nombreCompleto(actualizado)
            );

            return actualizado;
        });
    }

    public boolean eliminarCliente(Integer id) {
        if (clienteRepository.existsById(id)) {
            clienteRepository.deleteById(id);

            bitacoraLogger.registrar(
                    "cliente",
                    id,
                    "ELIMINAR",
                    "Cliente #" + id + " eliminado"
            );

            return true;
        }
        return false;
    }

    private String nombreCompleto(Cliente cliente) {
        return (cliente.getNombre() != null ? cliente.getNombre() : "")
                + " " + (cliente.getApellidoPaterno() != null ? cliente.getApellidoPaterno() : "")
                + " " + (cliente.getApellidoMaterno() != null ? cliente.getApellidoMaterno() : "");
    }
}