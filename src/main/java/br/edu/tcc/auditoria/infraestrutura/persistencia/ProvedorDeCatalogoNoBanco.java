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

// Classe que carrega uma carga de catálogo do banco para os repositórios em memória, onde fica a resolução por vigência, para essa lógica não ser repetida em SQL. Entrega a carga mais recente, para auditar, ou a de uma versão, para reabrir uma análise; a segunda porta foi acrescentada na Etapa 11, com a mesma montagem.
@Component
class ProvedorDeCatalogoNoBanco implements ProvedorDeCatalogo, ProvedorDeCatalogoPorVersao {

    private final CargaCatalogoJpa cargas;
    private final CoberturaCatalogoJpa coberturas;
    private final NaturezaDaCargaNoBanco naturezas;
    private final ClassificacaoTributariaJpa classificacoes;
    private final RegistroNcmJpa ncms;
    private final ItemAnexoJpa itensDeAnexo;
    private final AliquotaVigenteJpa aliquotas;

    // Construtor que recebe os repositórios de cada tabela do catálogo.
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

    // Carrega a carga mais recente; recusa se nenhuma foi importada.
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

    // Carrega a carga daquela versão, ou vazio se ela não está mais gravada; nunca cai na mais recente, para não mostrar ao lado de um apontamento uma tabela que não o produziu.
    @Override
    @Transactional(readOnly = true)
    public Optional<CatalogoParaAuditoria> daVersao(String versao) {
        if (versao == null || versao.isBlank()) {
            return Optional.empty();
        }
        return cargas.findByVersao(versao).map(this::montar);
    }

    // Método auxiliar que monta o catálogo com a cobertura, a procedência e os quatro repositórios em memória.
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

    // Método auxiliar que lê a cobertura declarada da carga, tabela por tabela.
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

    // Método auxiliar que exige a cobertura de uma tabela; recusa carga sem ela.
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
