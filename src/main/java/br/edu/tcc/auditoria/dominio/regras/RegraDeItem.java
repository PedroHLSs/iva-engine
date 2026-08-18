package br.edu.tcc.auditoria.dominio.regras;

import br.edu.tcc.auditoria.dominio.Achado;
import br.edu.tcc.auditoria.dominio.Documento;
import br.edu.tcc.auditoria.dominio.Evidencia;
import br.edu.tcc.auditoria.dominio.ItemDocumento;
import br.edu.tcc.auditoria.dominio.OrigemEvidencia;
import br.edu.tcc.auditoria.dominio.PeriodoVigencia;
import br.edu.tcc.auditoria.dominio.ValorEmRisco;
import br.edu.tcc.auditoria.dominio.catalogo.ContextoNormativo;
import br.edu.tcc.auditoria.dominio.catalogo.ProcedenciaNormativa;
import br.edu.tcc.auditoria.dominio.excecao.RegraInvalida;

import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;

/**
 * Base das regras que julgam um item de cada vez.
 *
 * <p>Não contém regra nenhuma: só remove das implementações a repetição de
 * montar {@link Avaliacao} e {@link Achado} com a mesma identificação. O ganho
 * não é digitação, é uniformidade — a identificação do achado passa a vir sempre
 * de {@link #id()}, {@link #versao()} e {@link #severidade()} da própria regra, e
 * não há como uma implementação declarar uma severidade e emitir achado com
 * outra.</p>
 *
 * <p>{@link #avaliar} é final e faz apenas a checagem de argumentos, delegando a
 * {@link #avaliarItem}. Argumento nulo é defeito de quem chamou, e por isso é
 * exceção — diferente de dado que faltou, que é {@code NAO_AVALIADO}.</p>
 */
public abstract class RegraDeItem implements RegraAuditoria {

    @Override
    public final Avaliacao avaliar(ItemDocumento item, Documento documento, ContextoNormativo contexto) {
        exigirArgumento(item, "item");
        exigirArgumento(documento, "documento");
        exigirArgumento(contexto, "contexto normativo");
        return avaliarItem(item, documento, contexto);
    }

    /** A pergunta que esta regra faz. Ver o contrato em {@link RegraAuditoria}. */
    protected abstract Avaliacao avaliarItem(
            ItemDocumento item, Documento documento, ContextoNormativo contexto);

    /** A regra foi aplicada por inteiro e nada encontrou. */
    protected final Avaliacao conforme(ItemDocumento item, Documento documento) {
        return Avaliacao.conforme(id(), versao(), documento.chaveAcesso(), OptionalInt.of(item.numeroItem()));
    }

    /** A regra não teve como julgar. O motivo é obrigatório e vai para o relatório. */
    protected final Avaliacao naoAvaliada(ItemDocumento item, Documento documento, String motivo) {
        return Avaliacao.naoAvaliada(
                id(), versao(), documento.chaveAcesso(), OptionalInt.of(item.numeroItem()), motivo);
    }

    /** A regra encontrou incoerência. */
    protected final Avaliacao comAchado(
            ItemDocumento item,
            Documento documento,
            List<Evidencia> evidencias,
            String fundamentoNormativo,
            PeriodoVigencia vigenciaAplicada,
            ValorEmRisco valorEmRisco) {

        return Avaliacao.comAchado(new Achado(
                id(),
                versao(),
                severidade(),
                documento.chaveAcesso(),
                OptionalInt.of(item.numeroItem()),
                evidencias,
                fundamentoNormativo,
                vigenciaAplicada,
                valorEmRisco));
    }

    /** Evidência de um campo lido do próprio documento. */
    protected static Evidencia doDocumento(String campo, ItemDocumento item, String valorEncontrado) {
        return new Evidencia(
                campo,
                Optional.of(valorEncontrado),
                Optional.empty(),
                new OrigemEvidencia.DoDocumento("item %d".formatted(item.numeroItem())));
    }

    /**
     * Evidência que opõe o declarado ao que o catálogo trazia.
     *
     * @param valorEncontrado o que o documento declarou; vazio quando o campo não veio
     * @param valorEsperado   o que a tabela indicava; vazio quando não há referência a opor
     */
    protected static Evidencia daTabela(
            String campo,
            String nomeDaTabela,
            String fonteNormativa,
            Optional<String> valorEncontrado,
            Optional<String> valorEsperado) {

        return new Evidencia(
                campo,
                valorEncontrado,
                valorEsperado,
                new OrigemEvidencia.DeTabelaNormativa(nomeDaTabela, fonteNormativa));
    }

    /**
     * Cobertura declarada de uma tabela do catálogo, exigida na construção da
     * regra que precisa dela.
     *
     * <p>Ver {@link CoberturaDoCatalogo} para o motivo de a cobertura existir.</p>
     */
    protected static ProcedenciaNormativa exigirCobertura(ProcedenciaNormativa cobertura, String tabela) {
        if (cobertura == null) {
            throw new RegraInvalida(
                    ("A regra precisa da cobertura declarada da tabela de %s: sem ela não há como "
                            + "distinguir \"o catálogo não traz este registro\" de \"esta tabela não foi "
                            + "carregada para a data do documento\".").formatted(tabela));
        }
        return cobertura;
    }

    private void exigirArgumento(Object valor, String nomeDoArgumento) {
        if (valor == null) {
            throw new RegraInvalida(
                    "A regra %s foi chamada sem %s.".formatted(id(), nomeDoArgumento));
        }
    }
}
