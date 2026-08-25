package br.com.gestpro.cliente.service;

import br.com.gestpro.auth.model.Usuario;
import br.com.gestpro.auth.repository.UsuarioRepository;
import br.com.gestpro.cliente.dto.ClienteDTO;
import br.com.gestpro.cliente.dto.ClienteRequest;
import br.com.gestpro.cliente.model.Cliente;
import br.com.gestpro.cliente.repository.ClienteRepository;
import br.com.gestpro.empresa.model.Empresa;
import br.com.gestpro.empresa.repository.EmpresaRepository;
import br.com.gestpro.infra.exception.ApiException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ClienteServiceImpl {

    private final ClienteRepository clienteRepository;
    private final UsuarioRepository usuarioRepository;
    private final EmpresaRepository empresaRepository;

    private Empresa validarEmpresa(Long empresaId, String email) {
        Empresa emp = empresaRepository.findById(empresaId)
                .orElseThrow(() -> new ApiException("Empresa não encontrada", HttpStatus.NOT_FOUND, "/clientes"));
        if (!emp.getDono().getEmail().equals(email))
            throw new ApiException("Sem permissão para esta empresa.", HttpStatus.FORBIDDEN, "/clientes");
        return emp;
    }

    @Transactional
    public ClienteDTO criar(ClienteRequest req, String emailUsuario) {
        if (req.getEmpresaId() == null)
            throw new ApiException("empresaId é obrigatório", HttpStatus.BAD_REQUEST, "/clientes");
        if (req.getNome() == null || req.getNome().isBlank())
            throw new ApiException("Nome é obrigatório", HttpStatus.BAD_REQUEST, "/clientes");

        String tipo = normalizarTipo(req.getTipo());
        String email = normalizarEmail(req.getEmail());

        if (email != null && clienteRepository
                .existsByEmailIgnoreCaseAndEmpresaIdAndTipoAndAtivoTrue(
                        email,
                        req.getEmpresaId(),
                        tipo
                )) {
            throw new ApiException(
                    "E-mail já cadastrado para outro " + rotuloTipo(tipo)
                            + " nesta empresa.",
                    HttpStatus.CONFLICT,
                    "/clientes"
            );
        }

        Usuario usuario = usuarioRepository.findByEmail(emailUsuario)
                .orElseThrow(() -> new ApiException("Usuário não encontrado", HttpStatus.NOT_FOUND, "/clientes"));

        Empresa empresa = validarEmpresa(req.getEmpresaId(), emailUsuario);

        Cliente c = new Cliente();
        c.setNome(req.getNome());
        c.setEmail(email);
        c.setTelefone(req.getTelefone());
        c.setCpf(req.getCpf());
        c.setCnpj(req.getCnpj());
        c.setContato(req.getContato());
        c.setObservacoes(req.getObservacoes());
        c.setTipo(tipo);
        c.setAtivo(true);
        c.setUsuario(usuario);
        c.setEmpresa(empresa);

        return new ClienteDTO(clienteRepository.save(c));
    }

    @Transactional
    public ClienteDTO atualizar(Long id, ClienteRequest req, String emailUsuario) {
        Cliente c = clienteRepository.findById(id)
                .orElseThrow(() -> new ApiException("Cliente não encontrado", HttpStatus.NOT_FOUND, "/clientes/" + id));

        if (!c.getUsuario().getEmail().equals(emailUsuario))
            throw new ApiException("Sem permissão.", HttpStatus.FORBIDDEN, "/clientes/" + id);

        String tipo = req.getTipo() != null ? normalizarTipo(req.getTipo()) : c.getTipo();
        String email = req.getEmail() != null ? normalizarEmail(req.getEmail()) : c.getEmail();

        if (email != null && clienteRepository
                .existsByEmailIgnoreCaseAndEmpresaIdAndTipoAndAtivoTrueAndIdNot(
                        email,
                        c.getEmpresa().getId(),
                        tipo,
                        id
                )) {
            throw new ApiException(
                    "E-mail já cadastrado para outro " + rotuloTipo(tipo)
                            + " nesta empresa.",
                    HttpStatus.CONFLICT,
                    "/clientes/" + id
            );
        }

        if (req.getNome()        != null) c.setNome(req.getNome());
        if (req.getEmail()       != null) c.setEmail(email);
        if (req.getTelefone()    != null) c.setTelefone(req.getTelefone());
        if (req.getCpf()         != null) c.setCpf(req.getCpf());
        if (req.getCnpj()        != null) c.setCnpj(req.getCnpj());
        if (req.getContato()     != null) c.setContato(req.getContato());
        if (req.getObservacoes() != null) c.setObservacoes(req.getObservacoes());
        if (req.getTipo()        != null) c.setTipo(tipo);

        return new ClienteDTO(clienteRepository.save(c));
    }

    @Transactional(readOnly = true)
    public ClienteDTO buscarPorId(Long id, String emailUsuario) {
        Cliente cliente = clienteRepository.findById(id)
                .orElseThrow(() -> new ApiException("Não encontrado", HttpStatus.NOT_FOUND, "/clientes/" + id));
        validarEmpresa(cliente.getEmpresa().getId(), emailUsuario);
        return new ClienteDTO(cliente);
    }

    @Transactional(readOnly = true)
    public List<ClienteDTO> listarPorEmpresa(Long empresaId, String emailUsuario) {
        validarEmpresa(empresaId, emailUsuario);
        return clienteRepository.findByEmpresaIdAndAtivoTrue(empresaId)
                .stream().map(ClienteDTO::new).toList();
    }

    @Transactional(readOnly = true)
    public List<ClienteDTO> listarPorEmpresaETipo(Long empresaId, String tipo, String emailUsuario) {
        validarEmpresa(empresaId, emailUsuario);
        return clienteRepository.findByEmpresaIdAndAtivoTrueAndTipo(empresaId, tipo.toUpperCase())
                .stream().map(ClienteDTO::new).toList();
    }

    @Transactional(readOnly = true)
    public List<ClienteDTO> listarAtivos(String emailUsuario) {
        return clienteRepository.findByUsuarioEmailAndAtivoTrue(emailUsuario)
                .stream().map(ClienteDTO::new).toList();
    }

    @Transactional
    public void desativar(Long id, String emailUsuario) {
        Cliente c = clienteRepository.findById(id)
                .orElseThrow(() -> new ApiException("Não encontrado", HttpStatus.NOT_FOUND, "/clientes/" + id));
        if (!c.getUsuario().getEmail().equals(emailUsuario))
            throw new ApiException("Sem permissão.", HttpStatus.FORBIDDEN, "/clientes/" + id);
        c.setAtivo(false);
        clienteRepository.save(c);
    }

    // legados para compatibilidade
    @Transactional
    public ClienteDTO criarCliente(Cliente cliente, String emailUsuario) {
        ClienteRequest req = new ClienteRequest();
        req.setNome(cliente.getNome()); req.setEmail(cliente.getEmail());
        req.setTelefone(cliente.getTelefone()); req.setCpf(cliente.getCpf());
        req.setTipo(cliente.getTipo() != null ? cliente.getTipo() : "CLIENTE");
        if (cliente.getEmpresa() != null) req.setEmpresaId(cliente.getEmpresa().getId());
        return criar(req, emailUsuario);
    }

    @Transactional(readOnly = true)
    public List<ClienteDTO> listarPorEmpresa2(Long empresaId, String emailUsuario) {
        return listarPorEmpresa(empresaId, emailUsuario);
    }

    @Transactional(readOnly = true)
    public List<ClienteDTO> listarClientesAtivos(String email) { return listarAtivos(email); }

    @Transactional(readOnly = true)
    public List<Cliente> listarClientesAtivos() { return clienteRepository.findByAtivoTrue(); }

    private String normalizarTipo(String tipo) {
        String normalizado = tipo == null || tipo.isBlank()
                ? "CLIENTE"
                : tipo.trim().toUpperCase();
        if (!normalizado.equals("CLIENTE") && !normalizado.equals("FORNECEDOR")) {
            throw new ApiException(
                    "Tipo deve ser CLIENTE ou FORNECEDOR.",
                    HttpStatus.BAD_REQUEST,
                    "/clientes"
            );
        }
        return normalizado;
    }

    private String normalizarEmail(String email) {
        if (email == null || email.isBlank()) return null;
        return email.trim().toLowerCase();
    }

    private String rotuloTipo(String tipo) {
        return tipo.equals("FORNECEDOR") ? "fornecedor" : "cliente";
    }
}
