package mz.ac.isutc.scad.no.documentos;

import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;

import mz.ac.isutc.scad.common.dfs.FilaHdfs;
import mz.ac.isutc.scad.common.relogio.RelogioCorrigido;
import mz.ac.isutc.scad.no.bd.FragmentosBd;
import mz.ac.isutc.scad.no.config.PropriedadesNo;
import mz.ac.isutc.scad.no.fornecedores.GatewayFornecedores;
import mz.ac.isutc.scad.no.loja.ArquivoRecibos;
import mz.ac.isutc.scad.no.loja.PedidoCliente;
import mz.ac.isutc.scad.no.ordens.OrdemCompra;
import mz.ac.isutc.scad.no.ordens.RepositorioOrdens;
import mz.ac.isutc.scad.no.transaccoes.Coordenador2PC;
import mz.ac.isutc.scad.no.transaccoes.Cronologia2PC;

/**
 * Passo 8 do fluxo e persistência partilhada no DFS:
 * <ul>
 *   <li>factura em PDF de cada ordem ENVIADA → {@code /scad/facturas/AAAA/MM/OC-xxx.pdf};</li>
 *   <li>recibo em PDF de cada pedido da loja autorizado → {@code /scad/recibos/AAAA/MM/PED-xxx.pdf};</li>
 *   <li>cronologia de cada 2PC coordenado por este nó → {@code /scad/2pc/<transacao>.json};</li>
 *   <li>2PC abortados → log de erros.</li>
 * </ul>
 * Tudo passa pela {@link FilaHdfs}: se o HDFS estiver em baixo, os ficheiros esperam no disco local.
 */
@Component
public class ArquivoDocumentos implements ArquivoRecibos {

    private static final Logger log = LoggerFactory.getLogger(ArquivoDocumentos.class);
    private static final DateTimeFormatter ANO_MES = DateTimeFormatter.ofPattern("yyyy/MM").withZone(ZoneOffset.UTC);

    private final FilaHdfs fila;
    private final RepositorioOrdens ordens;
    private final RelogioCorrigido relogio;
    private final RegistoErros erros;
    private final ObjectMapper json;
    private final String regiao;
    private final FragmentosBd fragmentos;

    public ArquivoDocumentos(FilaHdfs fila, RepositorioOrdens ordens, RelogioCorrigido relogio, RegistoErros erros,
                             ObjectMapper json, PropriedadesNo props, GatewayFornecedores gateway,
                             Coordenador2PC coordenador, FragmentosBd fragmentos) {
        this.fila = fila;
        this.ordens = ordens;
        this.relogio = relogio;
        this.erros = erros;
        this.json = json;
        this.regiao = props.regiao();
        this.fragmentos = fragmentos;
        gateway.aoEnviar(this::arquivarFactura);
        coordenador.aoTerminar(this::arquivar2pc);
    }

    /** Caminho da factura de uma ordem no HDFS. */
    public static String caminhoFactura(OrdemCompra o) {
        return "/scad/facturas/" + ANO_MES.format(o.tsCorrigido()) + "/" + o.id() + ".pdf";
    }

    void arquivarFactura(String ordemId) {
        ordens.procurar(ordemId).ifPresent(o -> {
            fila.guardar(caminhoFactura(o), GeradorFactura.gerar(o, regiao, relogio.agora()));
            log.info("Factura de {} gerada e colocada na fila do HDFS", ordemId);
            enviarFila();
        });
    }

    @Override
    public void arquivarRecibo(PedidoCliente pedido) {
        String cidade = fragmentos.jdbcLocal().queryForObject(
                "SELECT cidade FROM armazem WHERE id = ?", String.class, pedido.armazemId());
        fila.guardar(pedido.recibo(), GeradorRecibo.gerar(pedido, cidade, regiao, relogio.agora()));
        log.info("Recibo de {} gerado e colocado na fila do HDFS ({})", pedido.id(), pedido.recibo());
        enviarFila();
    }

    void arquivar2pc(Cronologia2PC.Vista c) {
        try {
            fila.guardar("/scad/2pc/" + c.transacaoId() + "-" + regiao + ".json",
                    json.writerWithDefaultPrettyPrinter().writeValueAsBytes(c));
        } catch (Exception e) {
            log.warn("Não foi possível serializar o 2PC {}: {}", c.transacaoId(), e.getMessage());
        }
        if (c.decisao() == Cronologia2PC.Decisao.ABORT) {
            erros.erro("2PC", "Transacção " + c.transacaoId() + " (" + c.tipo() + ") ABORTADA; votos " + c.votos()
                    + "; motivos " + c.motivosNao());
        }
    }

    /** Envia a fila local para o HDFS (a cada 5 s, e logo depois de cada documento). */
    @Scheduled(initialDelay = 5_000, fixedDelay = 5_000)
    public void enviarFila() {
        fila.esvaziar();
    }
}
