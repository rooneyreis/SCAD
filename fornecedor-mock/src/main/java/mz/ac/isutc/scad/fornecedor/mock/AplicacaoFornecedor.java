package mz.ac.isutc.scad.fornecedor.mock;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** Fornecedor simulado (REST + SOAP) para testar a integração e as falhas. */
@SpringBootApplication
public class AplicacaoFornecedor {

    public static void main(String[] args) {
        SpringApplication.run(AplicacaoFornecedor.class, args);
    }
}
