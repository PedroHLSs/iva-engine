package br.edu.tcc.auditoria.infraestrutura.persistencia;

import br.edu.tcc.auditoria.aplicacao.catalogo.CargaDeCatalogo;
import br.edu.tcc.auditoria.aplicacao.catalogo.NaturezaDaCarga;
import br.edu.tcc.auditoria.aplicacao.catalogo.RepositorioDeCargaDeCatalogo;
import br.edu.tcc.auditoria.aplicacao.catalogo.TabelaNormativa;
import br.edu.tcc.auditoria.dominio.catalogo.ProcedenciaNormativa;
import br.edu.tcc.auditoria.dominio.excecao.CatalogoInvalido;
import br.edu.tcc.auditoria.dominio.regras.CoberturaDoCatalogo;

import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

// Repositório que grava uma carga de catálogo importada. Cada importação vira uma carga nova, e a anterior continua gravada, para um relatório feito contra ela seguir conferível; dado normativo só entra por aqui, vindo de arquivo do usuário.
@Repository
class RepositorioDeCargaDeCatalogoNoBanco implements RepositorioDeCargaDeCatalogo {

    private final CargaCatalogoJpa cargas;
    private final CoberturaCatalogoJpa coberturas;
    private final NaturezaDaCargaJpa naturezas;
    private final ClassificacaoTributariaJpa classificacoes;
    private final RegistroNcmJpa ncms;
    private final ItemAnexoJpa itensDeAnexo;
    private final AliquotaVigenteJpa aliquotas;
    private final Clock relogio;

    // Construtor que recebe os repositórios de cada tabela do catálogo e o relógio.
    RepositorioDeCargaDeCatalogoNoBanco(
            CargaCatalogoJpa cargas,
            CoberturaCatalogoJpa coberturas,
            NaturezaDaCargaJpa naturezas,
            ClassificacaoTributariaJpa classificacoes,
            RegistroNcmJpa ncms,
            ItemAnexoJpa itensDeAnexo,
            AliquotaVigenteJpa aliquotas,
            Clock relogio) {
        this.cargas = cargas;
        this.coberturas = coberturas;
        this.naturezas = naturezas;
        this.classificacoes = classificacoes;
        this.ncms = ncms;
        this.itensDeAnexo = itensDeAnexo;
        this.aliquotas = aliquotas;
        this.relogio = relogio;
    }

    // Grava a carga, a cobertura, a procedência e os registros das quatro tabelas; recusa versão repetida.
    @Override
    @Transactional
    public void salvar(CargaDeCatalogo carga) {
        if (cargas.existsByVersao(carga.versao())) {
            throw new CatalogoInvalido(
                    ("Já existe carga de catálogo com a versão \"%s\". Escolha outra versão: duas cargas "
                            + "com o mesmo nome tornariam impossível dizer contra qual delas um relatório "
                            + "antigo foi produzido.").formatted(carga.versao()));
        }

        UUID cargaId = UUID.randomUUID();
        cargas.save(new CargaCatalogoEntidade(cargaId, carga.versao(), relogio.instant()));
        coberturas.saveAll(coberturaDe(carga.cobertura(), cargaId));
        naturezas.saveAll(naturezaDe(carga.natureza(), cargaId));

        classificacoes.saveAll(carga.classificacoesTributarias().stream()
                .map(registro -> MapeadorDeCatalogo.paraEntidade(registro, cargaId))
                .toList());
        ncms.saveAll(carga.registrosDeNcm().stream()
                .map(registro -> MapeadorDeCatalogo.paraEntidade(registro, cargaId))
                .toList());
        itensDeAnexo.saveAll(carga.itensDeAnexo().stream()
                .map(registro -> MapeadorDeCatalogo.paraEntidade(registro, cargaId))
                .toList());
        aliquotas.saveAll(carga.aliquotas().stream()
                .map(registro -> MapeadorDeCatalogo.paraEntidade(registro, cargaId))
                .toList());
    }

    // Busca a versão da carga mais recente, se houver.
    @Override
    @Transactional(readOnly = true)
    public Optional<String> versaoDaCargaMaisRecente() {
        return cargas.findTopByOrderByImportadoEmDescVersaoDesc().map(CargaCatalogoEntidade::versao);
    }

    // Método auxiliar que monta uma linha de procedência por tabela que tem registro; tabela vazia não gera linha, e isso é lido como não declarado, nunca como normativo.
    private static List<NaturezaDaCargaEntidade> naturezaDe(
            NaturezaDaCarga natureza, UUID cargaId) {

        List<NaturezaDaCargaEntidade> linhas = new ArrayList<>();
        natureza.declaradas().forEach((tabela, declarada) ->
                linhas.add(new NaturezaDaCargaEntidade(cargaId, tabela, declarada.name())));
        return linhas;
    }

    // Método auxiliar que monta as três linhas de cobertura da carga.
    private static List<CoberturaCatalogoEntidade> coberturaDe(
            CoberturaDoCatalogo cobertura, UUID cargaId) {
        List<CoberturaCatalogoEntidade> linhas = new ArrayList<>();
        linhas.add(linhaDe(cargaId, TabelaNormativa.CLASSIFICACAO_TRIBUTARIA,
                cobertura.classificacoesTributarias()));
        linhas.add(linhaDe(cargaId, TabelaNormativa.NCM, cobertura.ncm()));
        linhas.add(linhaDe(cargaId, TabelaNormativa.ITEM_ANEXO, cobertura.itensDeAnexo()));
        return linhas;
    }

    // Método auxiliar que monta a linha de cobertura de uma tabela.
    private static CoberturaCatalogoEntidade linhaDe(
            UUID cargaId, TabelaNormativa tabela, ProcedenciaNormativa procedencia) {
        return new CoberturaCatalogoEntidade(
                cargaId,
                tabela.name(),
                procedencia.vigenciaInicio(),
                procedencia.vigenciaFim().orElse(null),
                procedencia.fonteNormativa());
    }
}
