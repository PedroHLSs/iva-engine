package br.edu.tcc.auditoria.infraestrutura.persistencia;

import br.edu.tcc.auditoria.aplicacao.auditoria.CatalogoParaAuditoria;
import br.edu.tcc.auditoria.aplicacao.auditoria.ProvedorDeCatalogo;
import br.edu.tcc.auditoria.aplicacao.auditoria.ProvedorDeCatalogoPorVersao;
import br.edu.tcc.auditoria.aplicacao.catalogo.NaturezaDaCarga;
import br.edu.tcc.auditoria.aplicacao.catalogo.TabelaNormativa;
import br.edu.tcc.auditoria.dominio.catalogo.ProcedenciaNormativa;
import br.edu.tcc.auditoria.dominio.excecao.CatalogoInvalido;
import br.edu.tcc.auditoria.dominio.regras.CoberturaDoCatalogo;
import br.edu.tcc.auditoria.infraestrutura.catalogo.RepositorioAliquotaEmMemoria;
import br.edu.tcc.auditoria.infraestrutura.catalogo.RepositorioClassificacaoTributariaEmMemoria;
import br.edu.tcc.auditoria.infraestrutura.catalogo.RepositorioItemAnexoEmMemoria;
import br.edu.tcc.auditoria.infraestrutura.catalogo.RepositorioNcmEmMemoria;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Carrega a carga de catálogo mais recente e a entrega pronta para consulta por
 * data.
 *
 * <h2>Por que a carga inteira vai para a memória</h2>
 *
 * <p>Os repositórios em memória da etapa 2 não são um atalho: é neles que mora a
 * resolução por vigência e a recusa de vigências sobrepostas
 * ({@code SerieNormativa}). Consultar o banco registro a registro exigiria
 * reimplementar essa lógica em SQL, em duplicidade, com risco de as duas versões
 * divergirem — e a auditoria passaria a depender de qual caminho foi usado.</p>
 *
 * <p>O custo é aceitável porque um catálogo normativo é pequeno perto de um lote
 * de documentos, e porque ele é carregado uma vez por rodada, não uma vez por
 * documento.</p>
 *
 * <h2>Emenda da etapa de conferência: a segunda porta</h2>
 *
 * <p>Até a Etapa 10 esta classe respondia uma pergunta só — qual é o catálogo de
 * agora —, que é a pergunta de quem vai auditar. A tela de conferência faz outra:
 * qual era o catálogo <em>daquela</em> análise. Por isso a classe passou a
 * implementar também {@link ProvedorDeCatalogoPorVersao}.</p>
 *
 * <p>A montagem é a mesma, e de propósito: fossem dois carregadores, o tratamento
 * exibido na tela poderia divergir do que a auditoria usou por diferença de
 * implementação, e ninguém veria. O que muda entre os dois métodos é só qual linha
 * de {@code carga_catalogo} se busca.</p>
 */
@Component
class ProvedorDeCatalogoNoBanco implements ProvedorDeCatalogo, ProvedorDeCatalogoPorVersao {

    private final CargaCatalogoJpa cargas;
    private final CoberturaCatalogoJpa coberturas;
    private final NaturezaDaCargaNoBanco naturezas;
    private final ClassificacaoTributariaJpa classificacoes;
    private final RegistroNcmJpa ncms;
    private final ItemAnexoJpa itensDeAnexo;
    private final AliquotaVigenteJpa aliquotas;

    ProvedorDeCatalogoNoBanco(
            CargaCatalogoJpa cargas,
            CoberturaCatalogoJpa coberturas,
            NaturezaDaCargaNoBanco naturezas,
            ClassificacaoTributariaJpa classificacoes,
            RegistroNcmJpa ncms,
            ItemAnexoJpa itensDeAnexo,
            AliquotaVigenteJpa aliquotas) {
        this.cargas = cargas;
        this.coberturas = coberturas;
        this.naturezas = naturezas;
        this.classificacoes = classificacoes;
        this.ncms = ncms;
        this.itensDeAnexo = itensDeAnexo;
        this.aliquotas = aliquotas;
    }

    @Override
    @Transactional(readOnly = true)
    public CatalogoParaAuditoria carregar() {
        CargaCatalogoEntidade carga = cargas.findTopByOrderByImportadoEmDescVersaoDesc()
                .orElseThrow(() -> new CatalogoInvalido(
                        "Nenhum catálogo foi importado ainda. Rode \"importar-catalogo\" antes de "
                                + "auditar: sem catálogo toda regra responderia não avaliado, e o "
                                + "relatório teria aparência de auditoria feita."));

        return montar(carga);
    }

    /**
     * A carga daquela versão, vazio se ela não está mais gravada.
     *
     * <p>Não cai na mais recente quando não encontra. Cair seria mostrar, ao lado
     * de um apontamento de março, a tabela de setembro que não o produziu.</p>
     */
    @Override
    @Transactional(readOnly = true)
    public Optional<CatalogoParaAuditoria> daVersao(String versao) {
        if (versao == null || versao.isBlank()) {
            return Optional.empty();
        }
        return cargas.findByVersao(versao).map(this::montar);
    }

    private CatalogoParaAuditoria montar(CargaCatalogoEntidade carga) {
        UUID cargaId = carga.id();
        return new CatalogoParaAuditoria(
                carga.versao(),
                cobertura(cargaId, carga.versao()),
                naturezas.porCargaId(cargaId),
                new RepositorioClassificacaoTributariaEmMemoria(
                        classificacoes.findByCargaId(cargaId).stream()
                                .map(MapeadorDeCatalogo::paraDominio)
                                .toList()),
                new RepositorioNcmEmMemoria(
                        ncms.findByCargaId(cargaId).stream()
                                .map(MapeadorDeCatalogo::paraDominio)
                                .toList()),
                new RepositorioItemAnexoEmMemoria(
                        itensDeAnexo.findByCargaId(cargaId).stream()
                                .map(MapeadorDeCatalogo::paraDominio)
                                .toList()),
                new RepositorioAliquotaEmMemoria(
                        aliquotas.findByCargaId(cargaId).stream()
                                .map(MapeadorDeCatalogo::paraDominio)
                                .toList()));
    }

    private CoberturaDoCatalogo cobertura(UUID cargaId, String versao) {
        Map<String, ProcedenciaNormativa> porTabela = new LinkedHashMap<>();
        for (CoberturaCatalogoEntidade linha : coberturas.findByCargaId(cargaId)) {
            porTabela.put(linha.tabela(), MapeadorDeCatalogo.procedencia(
                    linha.vigenciaInicio(), linha.vigenciaFim(), linha.fonteNormativa()));
        }
        return new CoberturaDoCatalogo(
                exigir(porTabela, TabelaNormativa.CLASSIFICACAO_TRIBUTARIA, versao),
                exigir(porTabela, TabelaNormativa.NCM, versao),
                exigir(porTabela, TabelaNormativa.ITEM_ANEXO, versao));
    }

    private static ProcedenciaNormativa exigir(
            Map<String, ProcedenciaNormativa> porTabela, TabelaNormativa tabela, String versao) {
        ProcedenciaNormativa procedencia = porTabela.get(tabela.name());
        if (procedencia == null) {
            throw new CatalogoInvalido(
                    ("A carga \"%s\" não declarou cobertura para a tabela %s. Sem essa declaração não há "
                            + "como distinguir registro ausente do catálogo de tabela não carregada, e a "
                            + "auditoria apontaria com base em silêncio.")
                            .formatted(versao, tabela.name()));
        }
        return procedencia;
    }
}
