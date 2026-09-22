package br.edu.tcc.auditoria.aplicacao.conferencia;

import br.edu.tcc.auditoria.aplicacao.auditoria.ProvedorDeCatalogoPorVersao;
import br.edu.tcc.auditoria.aplicacao.catalogo.RepositorioDeCargaDeCatalogo;
import br.edu.tcc.auditoria.dominio.CodigoClassificacaoTributaria;
import br.edu.tcc.auditoria.dominio.Ncm;

import java.time.LocalDate;
import java.util.Optional;

/**
 * A tela da base tributária: o que a carga atual diz, na data que se perguntar.
 *
 * <h2>É o caso de uso que a D003 deixou em aberto</h2>
 *
 * <p>A D003 proibiu que uma regra escolhesse a data da consulta, e disse que "o
 * que vale hoje" seria um caso de uso próprio da aplicação, com data explícita.
 * Esta classe é esse caso de uso. A data é parâmetro obrigatório, não tem valor
 * padrão, e nem esta classe nem {@link BaseNormativa} chamam
 * {@code LocalDate.now()} — quem sabe que dia é hoje é quem está olhando a
 * tela.</p>
 *
 * <h2>A carga é a mais recente, e a resposta diz qual é</h2>
 *
 * <p>Diferente da tela de resultado, que resolve contra a carga registrada
 * naquela análise, aqui a pergunta é sobre a base carregada agora. São perguntas
 * diferentes e por isso duas entradas diferentes; o que não pode acontecer é uma
 * responder no lugar da outra em silêncio, e é por isso que a versão volta escrita
 * nos dois casos.</p>
 */
public final class ConsultaDaBaseTributaria {

    private final RepositorioDeCargaDeCatalogo cargas;
    private final ProvedorDeCatalogoPorVersao catalogos;

    public ConsultaDaBaseTributaria(
            RepositorioDeCargaDeCatalogo cargas, ProvedorDeCatalogoPorVersao catalogos) {

        if (cargas == null || catalogos == null) {
            throw new ConferenciaInvalida(
                    "A consulta à base tributária precisa do repositório de cargas e do provedor de "
                            + "catálogo por versão.");
        }
        this.cargas = cargas;
        this.catalogos = catalogos;
    }

    /**
     * A base na data indicada, com as consultas pontuais que tiverem sido pedidas.
     *
     * @param data   dia a que a consulta se refere, obrigatório
     * @param ncm    NCM a consultar, vazio se não se perguntou por nenhum
     * @param codigo {@code cClassTrib} a consultar, vazio se não se perguntou
     */
    public BaseTributariaEm em(
            LocalDate data, Optional<Ncm> ncm, Optional<CodigoClassificacaoTributaria> codigo) {

        if (data == null) {
            throw new ConferenciaInvalida(
                    "A consulta à base tributária exige data. Sem ela a resposta seria a de alguma data "
                            + "escolhida pelo sistema, e ninguém saberia qual.");
        }
        if (ncm == null || codigo == null) {
            throw new ConferenciaInvalida(
                    "Não perguntar por NCM ou por cClassTrib se representa com Optional.empty().");
        }

        BaseNormativa base = cargas.versaoDaCargaMaisRecente()
                .map(versao -> BaseNormativa.daVersao(catalogos, versao))
                .orElseGet(BaseNormativa::nenhumaCargaImportada);

        return new BaseTributariaEm(
                data,
                base.versaoDoCatalogo(),
                base.cargaDisponivel(),
                base.natureza(),
                base.cobertura(),
                base.aliquotasEm(data),
                ncm.map(consultado -> base.ncmEm(data, consultado)),
                ncm.map(consultado -> base.anexosEm(data, consultado)),
                codigo.map(consultado -> base.classificacaoEm(data, consultado)));
    }
}
