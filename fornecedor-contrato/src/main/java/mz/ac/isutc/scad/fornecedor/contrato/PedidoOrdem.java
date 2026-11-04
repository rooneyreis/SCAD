package mz.ac.isutc.scad.fornecedor.contrato;

import java.util.ArrayList;
import java.util.List;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlType;

/** Ordem de compra enviada ao fornecedor (o mesmo formato em JSON/REST e XML/SOAP). */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "PedidoOrdem", namespace = ServicoFornecedor.NAMESPACE)
public class PedidoOrdem {

    /** Identificador da ordem no SCAD; é também a chave de idempotência. */
    @XmlElement(required = true)
    private String ordemId;
    @XmlElement(required = true)
    private String fornecedorId;
    @XmlElement(required = true)
    private String produtoId;
    private String produtoNome;
    private int quantidade;
    /** Repartição da entrega pelos armazéns. */
    @XmlElement(name = "linha")
    private List<LinhaEntrega> linhas = new ArrayList<>();
    /** Data/hora do pedido (hora corrigida do SCAD, ISO-8601). */
    private String dataPedido;

    public PedidoOrdem() {
    }

    public PedidoOrdem(String ordemId, String fornecedorId, String produtoId, String produtoNome, int quantidade,
                       List<LinhaEntrega> linhas, String dataPedido) {
        this.ordemId = ordemId;
        this.fornecedorId = fornecedorId;
        this.produtoId = produtoId;
        this.produtoNome = produtoNome;
        this.quantidade = quantidade;
        this.linhas = new ArrayList<>(linhas);
        this.dataPedido = dataPedido;
    }

    public String getOrdemId() { return ordemId; }
    public void setOrdemId(String ordemId) { this.ordemId = ordemId; }
    public String getFornecedorId() { return fornecedorId; }
    public void setFornecedorId(String fornecedorId) { this.fornecedorId = fornecedorId; }
    public String getProdutoId() { return produtoId; }
    public void setProdutoId(String produtoId) { this.produtoId = produtoId; }
    public String getProdutoNome() { return produtoNome; }
    public void setProdutoNome(String produtoNome) { this.produtoNome = produtoNome; }
    public int getQuantidade() { return quantidade; }
    public void setQuantidade(int quantidade) { this.quantidade = quantidade; }
    public List<LinhaEntrega> getLinhas() { return linhas; }
    public void setLinhas(List<LinhaEntrega> linhas) { this.linhas = linhas; }
    public String getDataPedido() { return dataPedido; }
    public void setDataPedido(String dataPedido) { this.dataPedido = dataPedido; }

    @XmlAccessorType(XmlAccessType.FIELD)
    @XmlType(name = "LinhaEntrega", namespace = ServicoFornecedor.NAMESPACE)
    public static class LinhaEntrega {
        private String armazemId;
        private int quantidade;

        public LinhaEntrega() {
        }

        public LinhaEntrega(String armazemId, int quantidade) {
            this.armazemId = armazemId;
            this.quantidade = quantidade;
        }

        public String getArmazemId() { return armazemId; }
        public void setArmazemId(String armazemId) { this.armazemId = armazemId; }
        public int getQuantidade() { return quantidade; }
        public void setQuantidade(int quantidade) { this.quantidade = quantidade; }
    }
}
