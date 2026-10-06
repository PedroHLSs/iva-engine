package br.edu.tcc.auditoria.infraestrutura.persistencia;

import br.edu.tcc.auditoria.aplicacao.auditoria.CatalogoParaAuditoria;
import br.edu.tcc.auditoria.aplicacao.auditoria.ProvedorDeCatalogo;
import br.edu.tcc.auditoria.aplicacao.auditoria.ProvedorDeCatalogoPorVersao;
import br.edu.tcc.auditoria.dominio.excecao.CatalogoInvalido;

import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Clock;
import java.util.List;
import java.util.Map;

// Classe que entrega ao motor a carga mais recente e, na mesma transação, a sela. Selar na entrega, e não quando a análise termina de gravar, fecha a janela em que alguém editaria um rascunho que o motor já está lendo: a entrega e a edição travam a mesma linha. Acrescentada na Etapa 12, por fora do ProvedorDeCatalogoNoBanco, que continua montando a carga do mesmo jeito.
@Component
@Primary
class ProvedorDeCatalogoQueSela implements ProvedorDeCatalogo {

    private final ProvedorDeCatalogoPorVersao porVersao;
    private final JdbcTemplate jdbc;
    private final Clock relogio;

    // Construtor que recebe quem monta a carga pela versão, o acesso ao banco e o relógio.
    ProvedorDeCatalogoQueSela(ProvedorDeCatalogoPorVersao porVersao, JdbcTemplate jdbc, Clock relogio) {
        this.porVersao = porVersao;
        this.jdbc = jdbc;
        this.relogio = relogio;
    }

    // Trava a carga mais recente, sela se ainda não estava selada, e entrega o conteúdo dela; recusa se nenhuma foi importada.
    @Override
    @Transactional
    public CatalogoParaAuditoria carregar() {
        List<Map<String, Object>> maisRecente = jdbc.queryForList(
                "select id, versao from carga_catalogo order by importado_em desc, versao desc limit 1 for update");
        if (maisRecente.isEmpty()) {
            throw new CatalogoInvalido(
                    "Nenhum catálogo foi importado ainda. Rode \"importar-catalogo\" antes de "
                            + "auditar: sem catálogo toda regra responderia não avaliado, e o "
                            + "relatório teria aparência de auditoria feita.");
        }
        Object id = maisRecente.get(0).get("id");
        String versao = (String) maisRecente.get(0).get("versao");

        jdbc.update("update carga_catalogo set selada_em = ? where id = ? and selada_em is null",
                Timestamp.from(relogio.instant()), id);

        return porVersao.daVersao(versao).orElseThrow(() -> new CatalogoInvalido(
                "A carga \"%s\" sumiu entre a trava e a leitura.".formatted(versao)));
    }
}
