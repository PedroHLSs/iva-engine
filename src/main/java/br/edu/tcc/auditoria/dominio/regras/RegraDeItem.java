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

// Classe base das regras que avaliam um item por vez. Ela monta os resultados com o código, a versão e a gravidade da própria regra, para não haver diferença entre o que a regra diz e o que ela registra.
public abstract class RegraDeItem implements RegraAuditoria {

    // Confere se nada veio nulo e chama avaliarItem. Argumento nulo é erro de quem chamou, diferente de dado que faltou na nota.
    @Override
    public final Avaliacao avaliar(ItemDocumento item, Documento documento, ContextoNormativo contexto) {
        exigirArgumento(item, "item");
        exigirArgumento(documento, "documento");
        exigirArgumento(contexto, "contexto normativo");
        return avaliarItem(item, documento, contexto);
    }

    // Cada regra escreve aqui a sua verificação.
    protected abstract Avaliacao avaliarItem(
            ItemDocumento item, Documento documento, ContextoNormativo contexto);

    // Cria o resultado de quando a regra rodou inteira e não achou problema.
    protected final Avaliacao conforme(ItemDocumento item, Documento documento) {
        return Avaliacao.conforme(id(), versao(), documento.chaveAcesso(), OptionalInt.of(item.numeroItem()));
    }

    // Cria o resultado de quando a regra não conseguiu avaliar; o motivo é obrigatório.
    protected final Avaliacao naoAvaliada(ItemDocumento item, Documento documento, String motivo) {
        return Avaliacao.naoAvaliada(
                id(), versao(), documento.chaveAcesso(), OptionalInt.of(item.numeroItem()), motivo);
    }

    // Cria o resultado de quando a regra achou problema, com o código, a versão e a gravidade da própria regra.
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

    // Cria uma evidência com um valor lido da própria nota.
    protected static Evidencia doDocumento(String campo, ItemDocumento item, String valorEncontrado) {
        return new Evidencia(
                campo,
                Optional.of(valorEncontrado),
                Optional.empty(),
                new OrigemEvidencia.DoDocumento("item %d".formatted(item.numeroItem())));
    }

    // Cria uma evidência que mostra, lado a lado, o que a nota trouxe e o que a tabela do catálogo indicava.
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

    // Dá erro na criação da regra se o período coberto pela tabela não for informado.
    protected static ProcedenciaNormativa exigirCobertura(ProcedenciaNormativa cobertura, String tabela) {
        if (cobertura == null) {
            throw new RegraInvalida(
                    ("A regra precisa da cobertura declarada da tabela de %s: sem ela não há como "
                            + "distinguir \"o catálogo não traz este registro\" de \"esta tabela não foi "
                            + "carregada para a data do documento\".").formatted(tabela));
        }
        return cobertura;
    }

    // Método auxiliar que dá erro se algum argumento vier nulo.
    private void exigirArgumento(Object valor, String nomeDoArgumento) {
        if (valor == null) {
            throw new RegraInvalida(
                    "A regra %s foi chamada sem %s.".formatted(id(), nomeDoArgumento));
        }
    }
}
