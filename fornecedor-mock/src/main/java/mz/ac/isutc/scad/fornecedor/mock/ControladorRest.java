package mz.ac.isutc.scad.fornecedor.mock;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import mz.ac.isutc.scad.fornecedor.contrato.ConfirmacaoOrdem;
import mz.ac.isutc.scad.fornecedor.contrato.FalhaFornecedor;
import mz.ac.isutc.scad.fornecedor.contrato.PedidoOrdem;

/**
 * Rota REST/JSON (fornecedores "modernos") e controlo do simulador.
 *
 * <ul>
 *   <li>{@code POST /api/v1/ordens} com o cabeçalho {@code Idempotency-Key} (= ordemId):
 *       201 nova ordem, 200 ordem repetida, 400 dados inválidos, 503 indisponível, 500 erro.</li>
 *   <li>{@code GET/PUT /api/controlo}: interruptor NORMAL / EM_BAIXO / LENTO / ERRO.</li>
 * </ul>
 */
@RestController
public class ControladorRest {

    private final RecepcaoOrdens recepcao;

    public ControladorRest(RecepcaoOrdens recepcao) {
        this.recepcao = recepcao;
    }

    @PostMapping("/api/v1/ordens")
    public ResponseEntity<ConfirmacaoOrdem> submeter(
            @RequestHeader(name = "Idempotency-Key", required = false) String chave,
            @RequestBody PedidoOrdem pedido) throws FalhaFornecedor {
        if (chave == null || !chave.equals(pedido.getOrdemId())) {
            throw FalhaFornecedor.de("CHAVE_IDEMPOTENCIA", "O cabeçalho Idempotency-Key tem de ser igual ao ordemId", false);
        }
        ConfirmacaoOrdem c = recepcao.receber(pedido, "REST");
        return ResponseEntity.status(c.isDuplicado() ? HttpStatus.OK : HttpStatus.CREATED).body(c);
    }

    @GetMapping("/api/controlo")
    public Map<String, Object> estado() {
        return recepcao.estatisticas();
    }

    @PutMapping("/api/controlo")
    public Map<String, Object> mudarModo(@RequestBody Map<String, String> corpo) {
        recepcao.definirModo(RecepcaoOrdens.Modo.valueOf(corpo.getOrDefault("modo", "NORMAL")));
        return recepcao.estatisticas();
    }

    @ExceptionHandler(FalhaFornecedor.class)
    ProblemDetail falha(FalhaFornecedor e) {
        HttpStatus estado = switch (e.getFaultInfo().getCodigo()) {
            case "INDISPONIVEL" -> HttpStatus.SERVICE_UNAVAILABLE;
            case "ERRO_INTERNO" -> HttpStatus.INTERNAL_SERVER_ERROR;
            default -> HttpStatus.BAD_REQUEST;
        };
        ProblemDetail p = ProblemDetail.forStatusAndDetail(estado, e.getMessage());
        p.setTitle(e.getFaultInfo().getCodigo());
        p.setProperty("temporaria", e.getFaultInfo().isTemporaria());
        return p;
    }
}
