package mz.ac.isutc.scad.fornecedor.contrato;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlType;
import jakarta.xml.ws.WebFault;

/**
 * Erro do fornecedor, transmitido como SOAP Fault (com detalhe estruturado {@link Detalhe}).
 * {@code temporaria = true} indica que vale a pena tentar outra vez mais tarde.
 */
@WebFault(name = "FalhaFornecedor", targetNamespace = ServicoFornecedor.NAMESPACE)
public class FalhaFornecedor extends Exception {

    private final Detalhe faultInfo;

    public FalhaFornecedor(String mensagem, Detalhe faultInfo) {
        super(mensagem);
        this.faultInfo = faultInfo;
    }

    public FalhaFornecedor(String mensagem, Detalhe faultInfo, Throwable causa) {
        super(mensagem, causa);
        this.faultInfo = faultInfo;
    }

    public static FalhaFornecedor de(String codigo, String mensagem, boolean temporaria) {
        return new FalhaFornecedor(mensagem, new Detalhe(codigo, mensagem, temporaria));
    }

    public Detalhe getFaultInfo() {
        return faultInfo;
    }

    @XmlAccessorType(XmlAccessType.FIELD)
    @XmlType(name = "DetalheFalha", namespace = ServicoFornecedor.NAMESPACE)
    public static class Detalhe {
        private String codigo;
        private String mensagem;
        private boolean temporaria;

        public Detalhe() {
        }

        public Detalhe(String codigo, String mensagem, boolean temporaria) {
            this.codigo = codigo;
            this.mensagem = mensagem;
            this.temporaria = temporaria;
        }

        public String getCodigo() { return codigo; }
        public void setCodigo(String codigo) { this.codigo = codigo; }
        public String getMensagem() { return mensagem; }
        public void setMensagem(String mensagem) { this.mensagem = mensagem; }
        public boolean isTemporaria() { return temporaria; }
        public void setTemporaria(boolean temporaria) { this.temporaria = temporaria; }
    }
}
