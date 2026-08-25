package br.com.gestpro.cliente.service;

import br.com.gestpro.cliente.dto.DividaDTO;
import br.com.gestpro.cliente.dto.DividaRequest;
import br.com.gestpro.cliente.model.Cliente;
import br.com.gestpro.cliente.model.Divida;
import br.com.gestpro.cliente.repository.ClienteRepository;
import br.com.gestpro.cliente.repository.DividaRepository;
import br.com.gestpro.empresa.model.Empresa;
import br.com.gestpro.empresa.repository.EmpresaRepository;
import br.com.gestpro.infra.exception.ApiException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class DividaService {

    private final DividaRepository dividaRepository;
    private final ClienteRepository clienteRepository;
    private final EmpresaRepository empresaRepository;

    @Transactional
    public DividaDTO criar(DividaRequest req, String emailUsuario) {
        Cliente cliente = clienteRepository.findById(req.getClienteId())
                .orElseThrow(() -> new ApiException("Cliente não encontrado",
                        HttpStatus.NOT_FOUND, "/dividas"));
        Empresa empresa = empresaRepository.findById(req.getEmpresaId())
                .orElseThrow(() -> new ApiException("Empresa não encontrada",
                        HttpStatus.NOT_FOUND, "/dividas"));
        validarAcesso(empresa, emailUsuario, "/dividas");
        if (!cliente.getEmpresa().getId().equals(empresa.getId()))
            throw new ApiException("Cliente não pertence à empresa informada",
                    HttpStatus.BAD_REQUEST, "/dividas");
        if (req.getValor() == null || req.getValor().signum() <= 0)
            throw new ApiException("O valor da dívida deve ser maior que zero",
                    HttpStatus.BAD_REQUEST, "/dividas");

        Divida d = new Divida();
        d.setCliente(cliente);
        d.setEmpresa(empresa);
        d.setDescricao(req.getDescricao());
        d.setValor(req.getValor());
        d.setVencimento(req.getVencimento());

        return new DividaDTO(dividaRepository.save(d));
    }

    @Transactional(readOnly = true)
    public List<DividaDTO> listarPorCliente(Long clienteId, String emailUsuario) {
        Cliente cliente = clienteRepository.findById(clienteId)
                .orElseThrow(() -> new ApiException("Cliente não encontrado",
                        HttpStatus.NOT_FOUND, "/dividas/cliente/" + clienteId));
        validarAcesso(cliente.getEmpresa(), emailUsuario, "/dividas/cliente/" + clienteId);
        return dividaRepository
                .findByClienteIdOrderByStatusAscCriadoEmDesc(clienteId)
                .stream().map(DividaDTO::new).toList();
    }

    @Transactional
    public DividaDTO registrarPagamento(Long id, BigDecimal valor, String emailUsuario) {
        Divida d = dividaRepository.findById(id)
                .orElseThrow(() -> new ApiException("Dívida não encontrada",
                        HttpStatus.NOT_FOUND, "/dividas/" + id));
        validarAcesso(d.getEmpresa(), emailUsuario, "/dividas/" + id);
        if (valor == null || valor.signum() <= 0)
            throw new ApiException("O pagamento deve ser maior que zero",
                    HttpStatus.BAD_REQUEST, "/dividas/" + id);
        BigDecimal saldo = d.getValor().subtract(d.getValorPago());
        if (valor.compareTo(saldo) > 0)
            throw new ApiException("O pagamento não pode exceder o saldo da dívida",
                    HttpStatus.BAD_REQUEST, "/dividas/" + id);

        BigDecimal novoPago = d.getValorPago().add(valor);
        d.setValorPago(novoPago);

        if (novoPago.compareTo(d.getValor()) >= 0) {
            d.setStatus(Divida.StatusDivida.QUITADA);
            d.setQuitadoEm(LocalDateTime.now());
        } else {
            d.setStatus(Divida.StatusDivida.PARCIAL);
        }

        return new DividaDTO(dividaRepository.save(d));
    }

    @Transactional
    public void excluir(Long id, String emailUsuario) {
        Divida divida = dividaRepository.findById(id)
                .orElseThrow(() -> new ApiException("Dívida não encontrada",
                        HttpStatus.NOT_FOUND, "/dividas/" + id));
        validarAcesso(divida.getEmpresa(), emailUsuario, "/dividas/" + id);
        dividaRepository.delete(divida);
    }

    private void validarAcesso(Empresa empresa, String emailUsuario, String path) {
        if (!empresa.getDono().getEmail().equals(emailUsuario))
            throw new ApiException("Sem permissão para esta empresa", HttpStatus.FORBIDDEN, path);
    }
}
