package br.edu.tcc.auditoria.infraestrutura.persistencia;

import br.edu.tcc.auditoria.aplicacao.auditoria.CatalogoParaAuditoria;
import br.edu.tcc.auditoria.aplicacao.auditoria.ProvedorDeCatalogo;
import br.edu.tcc.auditoria.aplicacao.auditoria.ProvedorDeCatalogoPorVersao;
import br.edu.tcc.auditoria.aplicacao.catalogo.CargaEsperadaMudou;
import br.edu.tcc.auditoria.dominio.excecao.CatalogoInvalido;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.Timestamp;
import java.time.Clock;
import java.util.List;
import java.util.Map;

// Classe que entrega ao motor a carga mais recente só se ela for a que a tela mostrou antes de confirmar, e a sela na mesma transação. Serve para a medição de acurácia pela web: medir sela a carga, e a pessoa precisa ter visto qual antes. Se outra carga passou a ser a mais recente nesse meio tempo, recusa sem medir e sem selar nada. Acrescentada na Etapa 13.
@Component
public class ProvedorDaCargaEsperada {

    private final ProvedorDeCatalogoPorVersao porVersao;
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transacao;
    private final Clock relogio;

    // Construtor que recebe quem monta a carga pela versão, o acesso ao banco, a transação e o relógio.
    ProvedorDaCargaEsperada(
            ProvedorDeCatalogoPorVersao porVersao, JdbcTemplate jdbc, TransactionTemplate transacao, Clock relogio) {
        this.porVersao = porVersao;
        this.jdbc = jdbc;
        this.transacao = transacao;
        this.relogio = relogio;
    }

    // Devolve o provedor que só entrega a carga se a mais recente for a esperada.
    public ProvedorDeCatalogo para(String versaoEsperada) {
        if (versaoEsperada == null || versaoEsperada.isBlank()) {
            throw new CargaEsperadaMudou(
                    "O pedido não disse qual carga a tela mostrou. A medição sela a carga que usa, e isso "
                            + "precisa ter sido visto antes de confirmar.");
        }
        return () -> transacao.execute(situacao -> carregar(versaoEsperada));
    }

    // Método auxiliar que trava a carga mais recente, confere que é a esperada, sela e entrega.
    private CatalogoParaAuditoria carregar(String versaoEsperada) {
        List<Map<String, Object>> maisRecente = jdbc.queryForList(
                "select id, versao from carga_catalogo order by importado_em desc, versao desc limit 1 for update");
        if (maisRecente.isEmpty()) {
            throw new CatalogoInvalido("Nenhum catálogo foi importado ainda: não há contra o que medir.");
        }
        String versao = (String) maisRecente.get(0).get("versao");
        if (!versao.equals(versaoEsperada)) {
            throw new CargaEsperadaMudou(
                    ("A tela mostrou que a medição usaria a carga \"%s\", mas a mais recente agora é \"%s\". "
                            + "Nada foi medido e nada foi selado. Confira o aviso de novo antes de medir.")
                            .formatted(versaoEsperada, versao));
        }
        jdbc.update("update carga_catalogo set selada_em = ? where id = ? and selada_em is null",
                Timestamp.from(relogio.instant()), maisRecente.get(0).get("id"));
        return porVersao.daVersao(versao).orElseThrow(() -> new CatalogoInvalido(
                "A carga \"%s\" sumiu entre a trava e a leitura.".formatted(versao)));
    }
}
