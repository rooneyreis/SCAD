package mz.ac.isutc.scad.fornecedor.contrato;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlType;

/** Resposta do fornecedor: número de confirmação e prazo de entrega. */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "ConfirmacaoOrdem", namespace = ServicoFornecedor.NAMESPACE)
public class ConfirmacaoOrdem {

    private String ordemId;
    private String numeroConfirmacao;
    /** Data prevista de entrega (yyyy-MM-dd). */
    private String prazoEntrega;
    /** true se a ordem já tinha sido recebida antes (reenvio idempotente). */
    private boolean duplicado;

    public ConfirmacaoOrdem() {
    }

    public ConfirmacaoOrdem(String ordemId, String numeroConfirmacao, String prazoEntrega, boolean duplicado) {
        this.ordemId = ordemId;
        this.numeroConfirmacao = numeroConfirmacao;
        this.prazoEntrega = prazoEntrega;
        this.duplicado = duplicado;
    }

    public String getOrdemId() { return ordemId; }
    public void setOrdemId(String ordemId) { this.ordemId = ordemId; }
    public String getNumeroConfirmacao() { return numeroConfirmacao; }
    public void setNumeroConfirmacao(String numeroConfirmacao) { this.numeroConfirmacao = numeroConfirmacao; }
    public String getPrazoEntrega() { return prazoEntrega; }
    public void setPrazoEntrega(String prazoEntrega) { this.prazoEntrega = prazoEntrega; }
    public boolean isDuplicado() { return duplicado; }
    public void setDuplicado(boolean duplicado) { this.duplicado = duplicado; }

    public ConfirmacaoOrdem comoDuplicado() {
        return new ConfirmacaoOrdem(ordemId, numeroConfirmacao, prazoEntrega, true);
    }
}
