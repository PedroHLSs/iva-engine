package br.edu.tcc.auditoria.infraestrutura.persistencia;

import br.edu.tcc.auditoria.aplicacao.catalogo.AcervoDeCargas;
import br.edu.tcc.auditoria.aplicacao.catalogo.CargaDeCatalogo;
import br.edu.tcc.auditoria.aplicacao.catalogo.CargaNaoEncontrada;
import br.edu.tcc.auditoria.aplicacao.catalogo.CargaSelada;
import br.edu.tcc.auditoria.aplicacao.catalogo.EstadoDaCarga;
import br.edu.tcc.auditoria.aplicacao.catalogo.NaturezaDaCarga;
import br.edu.tcc.auditoria.aplicacao.catalogo.TabelaNormativa;
import br.edu.tcc.auditoria.dominio.catalogo.ProcedenciaNormativa;
import br.edu.tcc.auditoria.dominio.excecao.CatalogoInvalido;
import br.edu.tcc.auditoria.dominio.regras.CoberturaDoCatalogo;

import jakarta.persistence.EntityManager;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Clock;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

// Repositório que consulta, trava, edita e exclui cargas de catálogo. A carga selada é protegida duas vezes: aqui, que recusa antes de mexer, e nos gatilhos da V11, que recusam no banco mesmo que um caminho futuro esqueça de perguntar. Acrescentado na Etapa 12.
// Emenda de 04/10/2026 (D026): ganhou a trava do acervo (comAcervoTravado), tomada antes da trava da linha por todo caminho que muda qual é a carga mais recente ou o conteúdo de uma possível origem; e a mensagem de carga que não serve de origem passou a falar também da importação parcial, que usa a mesma remontagem.
@Repository
class AcervoDeCargasNoBanco implements AcervoDeCargas {

    private static final String ESTADO = """
            select c.id, c.versao, c.importado_em, c.selada_em, c.alterada_em, origem.versao as derivada_de,
                   (select count(*) from execucao_auditoria e where e.versao_catalogo = c.versao) as analises,
                   (select count(*) from classificacao_tributaria t where t.carga_id = c.id) as classificacoes,
                   (select count(*) from registro_ncm t where t.carga_id = c.id) as ncms,
                   (select count(*) from item_anexo t where t.carga_id = c.id) as anexos,
                   (select count(*) from aliquota_vigente t where t.carga_id = c.id) as aliquotas,
                   c.id = (select id from carga_catalogo order by importado_em desc, versao desc limit 1)
                       as mais_recente
              from carga_catalogo c
              left join carga_catalogo origem on origem.id = c.derivada_de
            """;

    // Nome da trava consultiva do acervo; o banco o transforma no número da trava. Não é dado normativo.
    private static final String CHAVE_DA_TRAVA_DO_ACERVO = "acervo-de-cargas-de-catalogo";

    private final JdbcTemplate jdbc;
    private final EntityManager entityManager;
    private final NaturezaDaCargaNoBanco naturezas;
    private final CoberturaCatalogoJpa coberturas;
    private final NaturezaDaCargaJpa naturezasJpa;
    private final ClassificacaoTributariaJpa classificacoes;
    private final RegistroNcmJpa ncms;
    private final ItemAnexoJpa itensDeAnexo;
    private final AliquotaVigenteJpa aliquotas;
    private final AnexoDeclaradoJpa anexosDeclarados;
    private final Clock relogio;

    // Construtor que recebe o acesso ao banco, os repositórios de cada tabela e o relógio.
    AcervoDeCargasNoBanco(
            JdbcTemplate jdbc,
            EntityManager entityManager,
            NaturezaDaCargaNoBanco naturezas,
            CoberturaCatalogoJpa coberturas,
            NaturezaDaCargaJpa naturezasJpa,
            ClassificacaoTributariaJpa classificacoes,
            RegistroNcmJpa ncms,
            ItemAnexoJpa itensDeAnexo,
            AliquotaVigenteJpa aliquotas,
            AnexoDeclaradoJpa anexosDeclarados,
            Clock relogio) {
        this.jdbc = jdbc;
        this.entityManager = entityManager;
        this.naturezas = naturezas;
        this.coberturas = coberturas;
        this.naturezasJpa = naturezasJpa;
        this.classificacoes = classificacoes;
        this.ncms = ncms;
        this.itensDeAnexo = itensDeAnexo;
        this.aliquotas = aliquotas;
        this.anexosDeclarados = anexosDeclarados;
        this.relogio = relogio;
    }

    @Override
    @Transactional(readOnly = true)
    public List<EstadoDaCarga> listar() {
        return jdbc.query(ESTADO + " order by c.importado_em desc, c.versao desc", estado());
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<EstadoDaCarga> estado(String versao) {
        return jdbc.query(ESTADO + " where c.versao = ?", estado(), versao).stream().findFirst();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<CargaDeCatalogo> conteudo(String versao) {
        return idDa(versao).map(cargaId -> montar(cargaId, versao));
    }

    @Override
    @Transactional(readOnly = true)
    public boolean existeVersao(String versao) {
        return idDa(versao).isPresent();
    }

    // Trava a linha da carga com "for update", a mesma trava que a entrega ao motor usa para selar.
    @Override
    @Transactional
    public <T> T comCargaTravada(String versao, Function<EstadoDaCarga, T> operacao) {
        List<UUID> travada = jdbc.queryForList(
                "select id from carga_catalogo where versao = ? for update", UUID.class, versao);
        if (travada.isEmpty()) {
            throw new CargaNaoEncontrada("Não há carga de catálogo com a versão \"%s\".".formatted(versao));
        }
        EstadoDaCarga estado = estado(versao).orElseThrow();
        return operacao.apply(estado);
    }

    // Trava o acervo inteiro com uma trava consultiva de transação, e só então lê a carga mais recente. Acrescentado em 04/10/2026 (D026). Travar a tabela causaria espera em ciclo com a selagem (o insert com derivada_de espera a linha que o selador trava, e o selador espera a tabela), e travar só a linha da mais recente não serve: quem espera recebe a linha antiga, sem refazer a ordenação, e no acervo vazio não há linha. A trava é solta no fim da transação.
    @Override
    @Transactional
    public <T> T comAcervoTravado(Function<Optional<EstadoDaCarga>, T> operacao) {
        jdbc.query("select pg_advisory_xact_lock(hashtext(?))", (RowCallbackHandler) linha -> { },
                CHAVE_DA_TRAVA_DO_ACERVO);
        Optional<EstadoDaCarga> maisRecente = jdbc.query(
                ESTADO + " order by c.importado_em desc, c.versao desc limit 1", estado()).stream().findFirst();
        return operacao.apply(maisRecente);
    }

    // Troca o conteúdo do rascunho: apaga as linhas das tabelas e grava as novas, mantendo a versão e a data de importação.
    @Override
    @Transactional
    public void substituirConteudo(String versao, CargaDeCatalogo nova) {
        UUID cargaId = exigirRascunho(versao);
        // Esvazia o contexto do JPA: as linhas lidas para montar a origem têm a mesma chave de algumas que vão ser regravadas, e ele tentaria atualizar linha já apagada.
        entityManager.flush();
        entityManager.clear();
        for (String tabela : List.of("classificacao_tributaria", "registro_ncm", "item_anexo",
                "aliquota_vigente", "cobertura_catalogo", "natureza_da_carga", "anexo_declarado")) {
            jdbc.update("delete from " + tabela + " where carga_id = ?", cargaId);
        }
        gravarConteudo(cargaId, nova);
        jdbc.update("update carga_catalogo set alterada_em = ? where id = ?",
                Timestamp.from(relogio.instant()), cargaId);
    }

    // Grava a carga nova com a data de agora, o que a torna a mais recente, e aponta de qual carga ela veio.
    @Override
    @Transactional
    public void salvarDerivada(CargaDeCatalogo nova, String versaoDeOrigem) {
        UUID origemId = idDa(versaoDeOrigem).orElseThrow(() -> new CargaNaoEncontrada(
                "Não há carga de catálogo com a versão \"%s\".".formatted(versaoDeOrigem)));
        if (existeVersao(nova.versao())) {
            throw new CatalogoInvalido(
                    "Já existe carga de catálogo com a versão \"%s\".".formatted(nova.versao()));
        }
        UUID cargaId = UUID.randomUUID();
        jdbc.update("insert into carga_catalogo (id, versao, importado_em, derivada_de) values (?, ?, ?, ?)",
                cargaId, nova.versao(), Timestamp.from(relogio.instant()), origemId);
        gravarConteudo(cargaId, nova);
    }

    // Exclui o rascunho; as linhas das tabelas saem em cascata.
    @Override
    @Transactional
    public void excluir(String versao) {
        UUID cargaId = exigirRascunho(versao);
        jdbc.update("delete from carga_catalogo where id = ?", cargaId);
    }

    // Método auxiliar que recusa mexer em carga selada, antes de chegar ao gatilho do banco.
    private UUID exigirRascunho(String versao) {
        EstadoDaCarga estado = estado(versao).orElseThrow(() -> new CargaNaoEncontrada(
                "Não há carga de catálogo com a versão \"%s\".".formatted(versao)));
        if (estado.selada()) {
            throw new CargaSelada(
                    "A carga \"%s\" está selada e não muda mais.".formatted(versao));
        }
        return idDa(versao).orElseThrow();
    }

    // Método auxiliar que grava a cobertura, a procedência e os registros das quatro tabelas, e força a escrita para um gatilho recusar aqui dentro, e não no fim da transação.
    private void gravarConteudo(UUID cargaId, CargaDeCatalogo carga) {
        coberturas.saveAll(List.of(
                cobertura(cargaId, TabelaNormativa.CLASSIFICACAO_TRIBUTARIA,
                        carga.cobertura().classificacoesTributarias()),
                cobertura(cargaId, TabelaNormativa.NCM, carga.cobertura().ncm()),
                cobertura(cargaId, TabelaNormativa.ITEM_ANEXO, carga.cobertura().itensDeAnexo())));

        List<NaturezaDaCargaEntidade> procedencia = new ArrayList<>();
        carga.natureza().declaradas().forEach((tabela, natureza) ->
                procedencia.add(new NaturezaDaCargaEntidade(cargaId, tabela, natureza.name())));
        naturezasJpa.saveAll(procedencia);

        classificacoes.saveAll(carga.classificacoesTributarias().stream()
                .map(registro -> MapeadorDeCatalogo.paraEntidade(registro, cargaId)).toList());
        ncms.saveAll(carga.registrosDeNcm().stream()
                .map(registro -> MapeadorDeCatalogo.paraEntidade(registro, cargaId)).toList());
        itensDeAnexo.saveAll(carga.itensDeAnexo().stream()
                .map(registro -> MapeadorDeCatalogo.paraEntidade(registro, cargaId)).toList());
        aliquotas.saveAll(carga.aliquotas().stream()
                .map(registro -> MapeadorDeCatalogo.paraEntidade(registro, cargaId)).toList());
        anexosDeclarados.saveAll(carga.cobertura().anexosDeclarados().stream()
                .map(anexo -> AnexoDeclaradoEntidade.de(anexo, cargaId)).toList());
        entityManager.flush();
    }

    // Método auxiliar que monta a linha de cobertura de uma tabela.
    private static CoberturaCatalogoEntidade cobertura(
            UUID cargaId, TabelaNormativa tabela, ProcedenciaNormativa procedencia) {
        return new CoberturaCatalogoEntidade(cargaId, tabela.name(), procedencia.vigenciaInicio(),
                procedencia.vigenciaFim().orElse(null), procedencia.fonteNormativa());
    }

    // Método auxiliar que remonta a carga inteira a partir do banco, para servir de origem a uma edição.
    private CargaDeCatalogo montar(UUID cargaId, String versao) {
        Map<String, ProcedenciaNormativa> porTabela = new LinkedHashMap<>();
        for (CoberturaCatalogoEntidade linha : coberturas.findByCargaId(cargaId)) {
            porTabela.put(linha.tabela(), MapeadorDeCatalogo.procedencia(
                    linha.vigenciaInicio(), linha.vigenciaFim(), linha.fonteNormativa()));
        }
        NaturezaDaCarga natureza = naturezas.porCargaId(cargaId);
        try {
            return new CargaDeCatalogo(
                    versao,
                    new CoberturaDoCatalogo(
                            porTabela.get(TabelaNormativa.CLASSIFICACAO_TRIBUTARIA.name()),
                            porTabela.get(TabelaNormativa.NCM.name()),
                            porTabela.get(TabelaNormativa.ITEM_ANEXO.name()),
                            anexosDeclarados.findByCargaId(cargaId).stream()
                                    .map(AnexoDeclaradoEntidade::paraDominio).toList()),
                    natureza,
                    classificacoes.findByCargaId(cargaId).stream().map(MapeadorDeCatalogo::paraDominio).toList(),
                    ncms.findByCargaId(cargaId).stream().map(MapeadorDeCatalogo::paraDominio).toList(),
                    itensDeAnexo.findByCargaId(cargaId).stream().map(MapeadorDeCatalogo::paraDominio).toList(),
                    aliquotas.findByCargaId(cargaId).stream().map(MapeadorDeCatalogo::paraDominio).toList());
        } catch (RuntimeException naoRemonta) {
            throw new CatalogoInvalido(
                    // Emenda de 04/10/2026 (D026): a importação parcial usa a mesma remontagem. Até essa data o texto era: "A carga \"%s\" não pode servir de origem para uma edição: %s Cargas importadas antes da declaração de natureza (Etapa 11) não dizem a procedência de cada tabela, e a edição não inventa uma. Importe o catálogo inteiro como carga nova."
                    ("A carga \"%s\" não pode servir de origem para uma edição ou importação parcial: %s Cargas "
                            + "importadas antes da declaração de natureza (Etapa 11) não dizem a procedência de "
                            + "cada tabela, e o sistema não inventa uma. Importe o catálogo inteiro como carga nova.")
                            .formatted(versao, naoRemonta.getMessage()));
        }
    }

    // Método auxiliar que busca o identificador da carga pela versão.
    private Optional<UUID> idDa(String versao) {
        return jdbc.queryForList("select id from carga_catalogo where versao = ?", UUID.class, versao)
                .stream().findFirst();
    }

    // Método auxiliar que lê uma linha da consulta de estado.
    private RowMapper<EstadoDaCarga> estado() {
        return (linha, numero) -> {
            UUID cargaId = linha.getObject("id", UUID.class);
            return new EstadoDaCarga(
                    linha.getString("versao"),
                    linha.getTimestamp("importado_em").toInstant(),
                    Optional.ofNullable(linha.getTimestamp("selada_em")).map(Timestamp::toInstant),
                    linha.getLong("analises"),
                    Optional.ofNullable(linha.getString("derivada_de")),
                    Optional.ofNullable(linha.getTimestamp("alterada_em")).map(Timestamp::toInstant),
                    linha.getBoolean("mais_recente"),
                    linha.getInt("classificacoes"),
                    linha.getInt("ncms"),
                    linha.getInt("anexos"),
                    linha.getInt("aliquotas"),
                    naturezas.porCargaId(cargaId));
        };
    }
}
