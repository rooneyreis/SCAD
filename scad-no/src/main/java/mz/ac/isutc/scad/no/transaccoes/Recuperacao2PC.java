package mz.ac.isutc.scad.no.transaccoes;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import mz.ac.isutc.scad.common.relogio.RelogioCorrigido;
import mz.ac.isutc.scad.no.bd.FragmentosBd;
import mz.ac.isutc.scad.no.transaccoes.Cronologia2PC.Decisao;

/**
 * Recuperação do coordenador do 2PC (corre no arranque e a cada 10 s).
 *
 * <ol>
 *   <li><b>Decisão gravada, fase 2 incompleta</b> (o coordenador morreu entre as fases, ou um
 *       participante estava em baixo): aplica a decisão do log a cada participante.</li>
 *   <li><b>Transacção preparada sem decisão gravada</b> (o coordenador morreu durante a votação):
 *       ninguém pode ter feito COMMIT, por isso é seguro anular ("presumed abort"). Só se consideram
 *       as transacções deste coordenador preparadas há mais de 30 s.</li>
 * </ol>
 */
@Component
public class Recuperacao2PC {

    private static final Logger log = LoggerFactory.getLogger(Recuperacao2PC.class);

    private final FragmentosBd fragmentos;
    private final Log2PC log2pc;
    private final Coordenador2PC coordenador;
    private final RelogioCorrigido relogio;

    public Recuperacao2PC(FragmentosBd fragmentos, Log2PC log2pc, Coordenador2PC coordenador, RelogioCorrigido relogio) {
        this.fragmentos = fragmentos;
        this.log2pc = log2pc;
        this.coordenador = coordenador;
        this.relogio = relogio;
    }

    @Scheduled(initialDelay = 5_000, fixedDelay = 10_000)
    public void recuperar() {
        try {
            concluirDecisoesPendentes();
            anularPreparadasSemDecisao();
        } catch (DataAccessException e) {
            log.debug("Recuperação 2PC adiada: {}", e.getMessage());
        }
    }

    void concluirDecisoesPendentes() {
        String eu = fragmentos.regiaoLocal();
        for (Log2PC.Entrada e : log2pc.porConcluir()) {
            Cronologia2PC c = new Cronologia2PC(e.transacaoId(), e.tipo() + " (recuperação)", eu,
                    e.participantes(), relogio.agora());
            c.decisao(e.decisao());
            c.evento(relogio.agora(), "Recuperação: decisão " + e.decisao() + " encontrada no log");
            boolean tudo = true;
            for (String p : e.participantes()) {
                if (!fragmentos.regioes().contains(p)) {
                    tudo = false;
                    continue;
                }
                boolean ok = coordenador.concluir(p, Coordenador2PC.gid(eu, e.transacaoId(), p), e.decisao());
                c.concluido(p, ok);
                c.evento(relogio.agora(), p + (ok ? ": " + e.decisao() + " PREPARED aplicado" : ": ainda inacessível"));
                tudo &= ok;
            }
            if (tudo) {
                log2pc.marcarConcluida(e.transacaoId());
                log.warn("Recuperação 2PC: transacção {} concluída com {}", e.transacaoId(), e.decisao());
            }
            c.fim(relogio.agora());
            coordenador.registarCronologia(c);
        }
    }

    void anularPreparadasSemDecisao() {
        String eu = fragmentos.regiaoLocal();
        for (String p : fragmentos.regioes()) {
            List<String> gids;
            try {
                gids = fragmentos.jdbc(p).queryForList("""
                        SELECT gid FROM pg_prepared_xacts
                         WHERE gid LIKE ? AND prepared < now() - interval '30 seconds'
                        """, String.class, eu + "|%");
            } catch (DataAccessException e) {
                continue;   // fragmento em baixo: tenta-se no próximo ciclo
            }
            for (String gid : gids) {
                String transacaoId = gid.split("\\|")[1];
                if (!log2pc.existe(transacaoId)) {
                    coordenador.concluir(p, gid, Decisao.ABORT);
                    log.warn("Recuperação 2PC: {} preparada sem decisão no log -> ROLLBACK PREPARED", gid);
                }
            }
        }
    }
}
