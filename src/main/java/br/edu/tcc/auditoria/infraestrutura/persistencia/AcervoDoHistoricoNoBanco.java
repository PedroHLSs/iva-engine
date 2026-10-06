package br.edu.tcc.auditoria.infraestrutura.persistencia;

import br.edu.tcc.auditoria.aplicacao.conferencia.EstadoDeConferencia;
import br.edu.tcc.auditoria.aplicacao.historico.AcervoDoHistorico;
import br.edu.tcc.auditoria.aplicacao.historico.FiltroDoHistorico;
import br.edu.tcc.auditoria.aplicacao.historico.LinhaDoHistorico;
import br.edu.tcc.auditoria.aplicacao.historico.PaginaDoHistorico;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

// Repositório do histórico de análises, nas tabelas autoria_da_execucao e resumo_da_execucao da V13. Filtra e pagina no banco: o navegador recebe só a página pedida. As datas do filtro são dias no fuso da máquina onde o sistema roda. Acrescentado na Etapa 13.
// Emenda de 04/10/2026 (D020): a contagem de produtos só é lida como contagem quando foi medida — a execução gravou os itens que leu, ou não leu item nenhum. Fora disso a linha sai sem contagem, com o motivo, e os filtros de quantidade e de situação não a excluem: excluir por um valor que não foi medido esconde resultado real. Até essa data o resumo gravava zeros para a execução do comando auditar, e o filtro de mínimo de divergências a tirava do resultado. Os resumos já gravados com esses zeros continuam no banco e não são lidos.
@Repository
class AcervoDoHistoricoNoBanco implements AcervoDoHistorico {

    // A execução tem contagem medida quando gravou os itens que leu, ou quando não leu item nenhum: zero produtos ali é medição.
    static final String CONTAGEM_MEDIDA =
            "(e.quantidade_itens = 0 or exists (select 1 from item_da_execucao i where i.execucao_id = e.id))";

    // Motivo da linha medida cujo resumo ainda não foi gravado: a análise terminou entre o cálculo dos resumos e a busca.
    static final String CONTAGEM_EM_CALCULO =
            "a contagem desta execução ainda está sendo calculada: recarregue a página.";

    private static final String DE = """
            from execucao_auditoria e
            left join resumo_da_execucao r on r.execucao_id = e.id
            left join autoria_da_execucao a on a.execucao_id = e.id
            left join usuario u on u.id = a.usuario_id
            where 1 = 1""";

    private final JdbcTemplate jdbc;

    // Construtor que recebe o acesso ao banco.
    AcervoDoHistoricoNoBanco(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    @Transactional
    public void registrarExecutor(UUID execucaoId, UUID usuarioId, Instant quando) {
        jdbc.update("insert into autoria_da_execucao (execucao_id, usuario_id, registrado_em) values (?, ?, ?)",
                execucaoId, usuarioId, Timestamp.from(quando));
    }

    @Override
    @Transactional(readOnly = true)
    public List<UUID> execucoesSemResumo() {
        // Também as que têm resumo sem contagem e passaram a ter itens: o resumo foi calculado antes de a análise registrar o que leu.
        return jdbc.queryForList("select e.id from execucao_auditoria e where not exists"
                + " (select 1 from resumo_da_execucao r where r.execucao_id = e.id)"
                + " or (" + CONTAGEM_MEDIDA + " and exists (select 1 from resumo_da_execucao r"
                + " where r.execucao_id = e.id and r.possivel_divergencia is null))", UUID.class);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean contagemMedida(UUID execucaoId) {
        Boolean medida = jdbc.queryForObject(
                "select " + CONTAGEM_MEDIDA + " from execucao_auditoria e where e.id = ?", Boolean.class, execucaoId);
        return Boolean.TRUE.equals(medida);
    }

    @Override
    @Transactional
    public void gravarResumo(UUID execucaoId, Optional<Map<EstadoDeConferencia, Integer>> porEstado,
            Optional<Integer> comPendencia, Optional<EstadoDeConferencia> situacaoMaisGrave,
            Optional<String> motivo, Instant quando) {
        // Resumo sem contagem é substituído pelo medido; resumo medido não muda.
        jdbc.update("""
                insert into resumo_da_execucao (execucao_id, possivel_divergencia, requer_conferencia,
                    nao_foi_possivel_concluir, sem_divergencia_identificada, produtos_com_pendencia,
                    situacao_mais_grave, motivo_da_situacao_ausente, calculado_em)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?)
                on conflict (execucao_id) do update set
                    possivel_divergencia = excluded.possivel_divergencia,
                    requer_conferencia = excluded.requer_conferencia,
                    nao_foi_possivel_concluir = excluded.nao_foi_possivel_concluir,
                    sem_divergencia_identificada = excluded.sem_divergencia_identificada,
                    produtos_com_pendencia = excluded.produtos_com_pendencia,
                    situacao_mais_grave = excluded.situacao_mais_grave,
                    motivo_da_situacao_ausente = excluded.motivo_da_situacao_ausente,
                    calculado_em = excluded.calculado_em
                where resumo_da_execucao.possivel_divergencia is null
                    and excluded.possivel_divergencia is not null""",
                execucaoId,
                porEstado.map(contagem -> contagem.get(EstadoDeConferencia.POSSIVEL_DIVERGENCIA)).orElse(null),
                porEstado.map(contagem -> contagem.get(EstadoDeConferencia.REQUER_CONFERENCIA)).orElse(null),
                porEstado.map(contagem -> contagem.get(EstadoDeConferencia.NAO_FOI_POSSIVEL_CONCLUIR)).orElse(null),
                porEstado.map(contagem -> contagem.get(EstadoDeConferencia.SEM_DIVERGENCIA_IDENTIFICADA)).orElse(null),
                comPendencia.orElse(null),
                situacaoMaisGrave.map(Enum::name).orElse(null),
                motivo.orElse(null),
                Timestamp.from(quando));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<LinhaDoHistorico.ExecutorRegistrado> executorDe(UUID execucaoId) {
        return jdbc.query("select u.login, u.nome, u.ativo from autoria_da_execucao a join usuario u"
                        + " on u.id = a.usuario_id where a.execucao_id = ?",
                (resultado, numero) -> new LinhaDoHistorico.ExecutorRegistrado(
                        resultado.getString("login"), resultado.getString("nome"), resultado.getBoolean("ativo")),
                execucaoId).stream().findFirst();
    }

    // Monta a consulta com os filtros pedidos, conta o total e busca só a página.
    @Override
    @Transactional(readOnly = true)
    public PaginaDoHistorico buscar(FiltroDoHistorico filtro) {
        StringBuilder onde = new StringBuilder();
        List<Object> valores = new ArrayList<>();
        ZoneId fuso = ZoneId.systemDefault();

        filtro.de().ifPresent(dia -> {
            onde.append(" and e.data_hora >= ?");
            valores.add(Timestamp.from(dia.atStartOfDay(fuso).toInstant()));
        });
        filtro.ate().ifPresent(dia -> {
            onde.append(" and e.data_hora < ?");
            valores.add(Timestamp.from(dia.plusDays(1).atStartOfDay(fuso).toInstant()));
        });
        // D020: a execução sem contagem medida passa pelos filtros de situação e de quantidade, porque não há como classificá-la.
        filtro.situacaoMaisGrave().ifPresent(estado -> {
            onde.append(" and (not " + CONTAGEM_MEDIDA + " or r.situacao_mais_grave = ?)");
            valores.add(estado.name());
        });
        filtro.minimoDeDivergencias().ifPresent(minimo -> {
            onde.append(" and (not " + CONTAGEM_MEDIDA + " or r.possivel_divergencia >= ?)");
            valores.add(minimo);
        });
        filtro.maximoDeDivergencias().ifPresent(maximo -> {
            onde.append(" and (not " + CONTAGEM_MEDIDA + " or r.possivel_divergencia <= ?)");
            valores.add(maximo);
        });
        filtro.executor().login().ifPresent(login -> {
            onde.append(" and u.login = ?");
            valores.add(login);
        });
        if (filtro.executor().somenteNaoRegistrado()) {
            onde.append(" and a.execucao_id is null");
        }

        Long total = jdbc.queryForObject("select count(*) " + DE + onde, Long.class, valores.toArray());
        Long naoMedidas = jdbc.queryForObject(
                "select count(*) " + DE + onde + " and not " + CONTAGEM_MEDIDA, Long.class, valores.toArray());

        List<Object> daPagina = new ArrayList<>(valores);
        daPagina.add(filtro.tamanho());
        daPagina.add((long) filtro.pagina() * filtro.tamanho());
        List<LinhaDoHistorico> linhas = jdbc.query("""
                select e.id, e.data_hora, e.versao_catalogo, e.versao_conjunto_regras,
                       e.quantidade_documentos, e.quantidade_itens,
                       u.login, u.nome, u.ativo,
                       r.possivel_divergencia, r.requer_conferencia, r.nao_foi_possivel_concluir,
                       r.sem_divergencia_identificada, r.produtos_com_pendencia,
                       r.situacao_mais_grave, r.motivo_da_situacao_ausente,
                """ + CONTAGEM_MEDIDA + " as contagem_medida " + DE + onde + " order by e.data_hora desc, e.id desc limit ? offset ?",
                linha(), daPagina.toArray());

        List<LinhaDoHistorico.ExecutorRegistrado> executores = jdbc.query(
                "select distinct u.login, u.nome, u.ativo from autoria_da_execucao a"
                        + " join usuario u on u.id = a.usuario_id order by u.login",
                (resultado, numero) -> new LinhaDoHistorico.ExecutorRegistrado(
                        resultado.getString("login"), resultado.getString("nome"), resultado.getBoolean("ativo")));

        return new PaginaDoHistorico(linhas, total == null ? 0 : total, filtro.pagina(), filtro.tamanho(), executores,
                naoMedidas == null ? 0 : naoMedidas);
    }

    // Método auxiliar que lê uma linha do histórico.
    private static RowMapper<LinhaDoHistorico> linha() {
        return (resultado, numero) -> {
            // Medida e ainda sem resumo: a análise terminou entre o cálculo dos resumos e esta busca.
            boolean medida = resultado.getBoolean("contagem_medida");
            boolean comContagem = medida && resultado.getObject("possivel_divergencia") != null;
            String motivoSemContagem = medida ? CONTAGEM_EM_CALCULO : LinhaDoHistorico.CONTAGEM_NAO_REGISTRADA;
            Optional<Map<EstadoDeConferencia, Integer>> porEstado = Optional.empty();
            Optional<Integer> comPendencia = Optional.empty();
            if (comContagem) {
                Map<EstadoDeConferencia, Integer> contagem = new EnumMap<>(EstadoDeConferencia.class);
                contagem.put(EstadoDeConferencia.POSSIVEL_DIVERGENCIA, contagem(resultado, "possivel_divergencia"));
                contagem.put(EstadoDeConferencia.REQUER_CONFERENCIA, contagem(resultado, "requer_conferencia"));
                contagem.put(EstadoDeConferencia.NAO_FOI_POSSIVEL_CONCLUIR,
                        contagem(resultado, "nao_foi_possivel_concluir"));
                contagem.put(EstadoDeConferencia.SEM_DIVERGENCIA_IDENTIFICADA,
                        contagem(resultado, "sem_divergencia_identificada"));
                porEstado = Optional.of(contagem);
                comPendencia = Optional.of(contagem(resultado, "produtos_com_pendencia"));
            }
            String login = resultado.getString("login");
            // Sem contagem medida não há situação, mesmo que um resumo antigo tenha gravado uma.
            String situacao = comContagem ? resultado.getString("situacao_mais_grave") : null;
            String motivoDaSituacao = comContagem
                    ? resultado.getString("motivo_da_situacao_ausente")
                    : motivoSemContagem;
            return new LinhaDoHistorico(
                    resultado.getObject("id", UUID.class),
                    resultado.getTimestamp("data_hora").toInstant(),
                    resultado.getString("versao_catalogo"),
                    resultado.getString("versao_conjunto_regras"),
                    resultado.getInt("quantidade_documentos"),
                    resultado.getInt("quantidade_itens"),
                    login == null
                            ? Optional.empty()
                            : Optional.of(new LinhaDoHistorico.ExecutorRegistrado(
                                    login, resultado.getString("nome"), resultado.getBoolean("ativo"))),
                    porEstado,
                    comPendencia,
                    comContagem ? Optional.empty() : Optional.of(motivoSemContagem),
                    Optional.ofNullable(situacao).map(EstadoDeConferencia::valueOf),
                    Optional.ofNullable(motivoDaSituacao));
        };
    }

    // Método auxiliar que lê uma contagem gravada; só é chamado quando o resumo tem as cinco.
    private static int contagem(java.sql.ResultSet resultado, String coluna) throws java.sql.SQLException {
        return resultado.getInt(coluna);
    }
}
