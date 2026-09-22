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

/**
 * Grava uma carga de catálogo importada.
 *
 * <p>Cada importação vira uma carga nova, com identificador próprio, e as linhas
 * normativas ficam presas a ela. Importar de novo não apaga o catálogo anterior:
 * a auditoria passa a usar o mais recente, e o antigo continua ali para que um
 * relatório produzido contra ele siga conferível.</p>
 *
 * <p>Nenhum dado normativo entra por aqui sem ter vindo de um arquivo que o
 * usuário forneceu. Migration não insere nada; este é o único caminho.</p>
 */
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

    @Override
    @Transactional(readOnly = true)
    public Optional<String> versaoDaCargaMaisRecente() {
        return cargas.findTopByOrderByImportadoEmDescVersaoDesc().map(CargaCatalogoEntidade::versao);
    }

    /*
     * Uma linha por tabela que TEM registro. Tabela vazia não gera linha: a
     * natureza é declarada linha a linha no CSV, e arquivo só com cabeçalho não
     * tem onde declará-la. Ausência aqui é lida como ausência, nunca como
     * normativo — ver SituacaoDaNatureza.
     */
    private static List<NaturezaDaCargaEntidade> naturezaDe(
            NaturezaDaCarga natureza, UUID cargaId) {

        List<NaturezaDaCargaEntidade> linhas = new ArrayList<>();
        natureza.declaradas().forEach((tabela, declarada) ->
                linhas.add(new NaturezaDaCargaEntidade(cargaId, tabela, declarada.name())));
        return linhas;
    }

    private static List<CoberturaCatalogoEntidade> coberturaDe(
            CoberturaDoCatalogo cobertura, UUID cargaId) {
        List<CoberturaCatalogoEntidade> linhas = new ArrayList<>();
        linhas.add(linhaDe(cargaId, TabelaNormativa.CLASSIFICACAO_TRIBUTARIA,
                cobertura.classificacoesTributarias()));
        linhas.add(linhaDe(cargaId, TabelaNormativa.NCM, cobertura.ncm()));
        linhas.add(linhaDe(cargaId, TabelaNormativa.ITEM_ANEXO, cobertura.itensDeAnexo()));
        return linhas;
    }

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
