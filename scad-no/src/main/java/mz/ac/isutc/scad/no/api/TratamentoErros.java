package mz.ac.isutc.scad.no.api;

import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import mz.ac.isutc.scad.no.movimentos.ErrosMovimento.ArmazemForaDoFragmento;
import mz.ac.isutc.scad.no.movimentos.ErrosMovimento.ProdutoNaoEncontrado;
import mz.ac.isutc.scad.no.movimentos.ErrosMovimento.StockInsuficiente;
import mz.ac.isutc.scad.no.exclusao.ExclusaoMutua.ExclusaoIndisponivelException;
import mz.ac.isutc.scad.no.ordens.ServicoOrdens.EstadoInvalidoException;
import mz.ac.isutc.scad.no.ordens.ServicoOrdens.OrdemInexistenteException;

/** Converte excepções em respostas HTTP com corpo "problem+json" (RFC 9457). */
@RestControllerAdvice
public class TratamentoErros {

    private static final Logger log = LoggerFactory.getLogger(TratamentoErros.class);

    @ExceptionHandler(ProdutoNaoEncontrado.class)
    ProblemDetail produtoNaoEncontrado(ProdutoNaoEncontrado e) {
        return problema(HttpStatus.NOT_FOUND, "Produto não encontrado", e.getMessage());
    }

    @ExceptionHandler(StockInsuficiente.class)
    ProblemDetail stockInsuficiente(StockInsuficiente e) {
        return problema(HttpStatus.CONFLICT, "Stock insuficiente", e.getMessage());
    }

    @ExceptionHandler(ArmazemForaDoFragmento.class)
    ProblemDetail armazemForaDoFragmento(ArmazemForaDoFragmento e) {
        return problema(HttpStatus.UNPROCESSABLE_ENTITY, "Armazém de outro fragmento", e.getMessage());
    }

    @ExceptionHandler(OrdemInexistenteException.class)
    ProblemDetail ordemInexistente(OrdemInexistenteException e) {
        return problema(HttpStatus.NOT_FOUND, "Ordem não encontrada", e.getMessage());
    }

    @ExceptionHandler(EstadoInvalidoException.class)
    ProblemDetail estadoInvalido(EstadoInvalidoException e) {
        return problema(HttpStatus.CONFLICT, "Estado da ordem não permite a operação", e.getMessage());
    }

    @ExceptionHandler(ExclusaoIndisponivelException.class)
    ProblemDetail exclusaoIndisponivel(ExclusaoIndisponivelException e) {
        return problema(HttpStatus.SERVICE_UNAVAILABLE, "Exclusão mútua indisponível", e.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ProblemDetail argumentoInvalido(IllegalArgumentException e) {
        return problema(HttpStatus.BAD_REQUEST, "Pedido inválido", e.getMessage());
    }

    @ExceptionHandler(java.io.FileNotFoundException.class)
    ProblemDetail ficheiroInexistente(java.io.FileNotFoundException e) {
        return problema(HttpStatus.NOT_FOUND, "Ficheiro não encontrado", e.getMessage());
    }

    @ExceptionHandler(java.io.IOException.class)
    ProblemDetail hdfsIndisponivel(java.io.IOException e) {
        return problema(HttpStatus.SERVICE_UNAVAILABLE, "HDFS indisponível", e.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ProblemDetail dadosInvalidos(MethodArgumentNotValidException e) {
        String detalhe = e.getBindingResult().getFieldErrors().stream()
                .map(f -> f.getField() + ": " + f.getDefaultMessage())
                .collect(Collectors.joining("; "));
        return problema(HttpStatus.BAD_REQUEST, "Dados inválidos", detalhe);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ProblemDetail jsonInvalido(HttpMessageNotReadableException e) {
        return problema(HttpStatus.BAD_REQUEST, "Pedido mal formado", "O corpo do pedido não é JSON válido");
    }

    @ExceptionHandler(DataAccessException.class)
    ProblemDetail baseDadosIndisponivel(DataAccessException e) {
        log.warn("Erro de acesso à base de dados: {}", e.getMessage());
        return problema(HttpStatus.SERVICE_UNAVAILABLE, "Base de dados indisponível",
                "O fragmento de dados deste nó não está acessível neste momento");
    }

    private static ProblemDetail problema(HttpStatus estado, String titulo, String detalhe) {
        ProblemDetail p = ProblemDetail.forStatusAndDetail(estado, detalhe);
        p.setTitle(titulo);
        return p;
    }
}
