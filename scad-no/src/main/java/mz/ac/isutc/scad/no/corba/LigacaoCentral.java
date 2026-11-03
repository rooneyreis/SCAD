package mz.ac.isutc.scad.no.corba;

import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import mz.ac.isutc.scad.common.corba.ServicoNomes;
import mz.ac.isutc.scad.common.corba.ServidorCorba;
import mz.ac.isutc.scad.corba.AgenteStock;
import mz.ac.isutc.scad.corba.AgenteStockHelper;
import mz.ac.isutc.scad.corba.AlertaStock;
import mz.ac.isutc.scad.corba.CentralAuditoria;
import mz.ac.isutc.scad.corba.CentralAuditoriaHelper;
import mz.ac.isutc.scad.no.agente.CanalAlertas;
import mz.ac.isutc.scad.no.config.PropriedadesNo;

/**
 * Mantém este nó registado no Naming Service e na Central de Auditoria.
 *
 * <p>O registo é repetido periodicamente (é idempotente). Assim, se a Central ou o Naming
 * reiniciarem, ou se este nó reiniciar, o registo é refeito sem intervenção manual.
 */
@Component
public class LigacaoCentral implements CanalAlertas {

    private static final Logger log = LoggerFactory.getLogger(LigacaoCentral.class);

    private final String regiao;
    private final ServidorCorba servidor;
    private final AgenteStock referenciaAgente;

    private volatile CentralAuditoria central;
    private volatile boolean centralDisponivel;
    private volatile Instant ultimoRegisto;

    public LigacaoCentral(PropriedadesNo props, ServidorCorba servidor, AgenteStockImpl agente) {
        this.regiao = props.regiao();
        this.servidor = servidor;
        this.referenciaAgente = AgenteStockHelper.narrow(servidor.activar(ServicoNomes.nomeAgente(regiao), agente));
    }

    @Scheduled(initialDelay = 2_000, fixedDelay = 10_000)
    public void manterRegisto() {
        try {
            servidor.nomes().registar(ServicoNomes.nomeAgente(regiao), referenciaAgente);
            CentralAuditoria c = CentralAuditoriaHelper.narrow(
                    servidor.nomes().resolver(ServicoNomes.CENTRAL_AUDITORIA));
            c.registarAgente(regiao, referenciaAgente);
            central = c;
            ultimoRegisto = Instant.now();
            if (!centralDisponivel) {
                log.info("[{}] Registado no Naming Service e na Central de Auditoria", regiao);
            }
            centralDisponivel = true;
        } catch (RuntimeException e) {
            if (centralDisponivel || ultimoRegisto == null) {
                log.warn("[{}] Central/Naming indisponível: {}", regiao, e.toString());
            }
            centralDisponivel = false;
        }
    }

    /**
     * Envia um alerta à Central. Devolve false se a Central não estiver acessível.
     *
     * <p>{@code dispararAlerta} é {@code oneway}: o servidor não responde, por isso um envio para
     * um processo que não tem o objecto activo "parece" bem-sucedido e o alerta perde-se em silêncio.
     * Antes do envio fazemos {@code _non_existent()}, uma operação CORBA padrão <i>com</i> resposta,
     * que confirma que a Central está viva e tem o objecto activo. Um eventual duplicado
     * (alerta reenviado) é descartado pela Central através do {@code alertaId}.
     */
    @Override
    public boolean enviarAlerta(AlertaStock alerta) {
        CentralAuditoria c = central;
        if (c == null) {
            return false;
        }
        try {
            if (c._non_existent()) {
                log.warn("[{}] A referência da Central já não é válida; aguarda novo registo", regiao);
                centralDisponivel = false;
                return false;
            }
            c.dispararAlerta(alerta);
            return true;
        } catch (RuntimeException e) {
            log.warn("[{}] Falha ao enviar alerta {}: {}", regiao, alerta.alertaId, e.toString());
            centralDisponivel = false;
            return false;
        }
    }

    public boolean centralDisponivel() {
        return centralDisponivel;
    }

    public Instant ultimoRegisto() {
        return ultimoRegisto;
    }
}
