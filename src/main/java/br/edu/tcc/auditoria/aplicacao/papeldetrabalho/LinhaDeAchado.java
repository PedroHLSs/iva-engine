package br.edu.tcc.auditoria.aplicacao.papeldetrabalho;

import br.edu.tcc.auditoria.dominio.Severidade;
import br.edu.tcc.auditoria.dominio.Uf;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Uma linha da aba de achados: tudo o que é preciso para conferir um
 * apontamento sem abrir o sistema.
 *
 * <h2>Identificação do documento sem identificar ninguém</h2>
 *
 * <p>{@code documentoPseudonimizado} é o resumo da chave de acesso, e não a
 * chave: os dígitos da chave carregam o CNPJ do emitente, e a exportação não
 * leva identificador em texto claro. O que permite achar a nota no sistema da
 * empresa são {@code modelo}, {@code serie}, {@code numero}, {@code dataEmissao}
 * e {@code ufEmitente} — numeração do próprio emitente e localização da
 * operação, nenhum deles dado de participante.</p>
 *
 * <h2>Um apontamento, várias evidências</h2>
 *
 * <p>Um apontamento pode ter mais de uma evidência, e a linha é uma só. As três
 * listas — campos, valores encontrados e valores esperados — têm o mesmo
 * tamanho e são alinhadas posição a posição: o campo da posição 2 corresponde ao
 * valor encontrado da posição 2. Na planilha isso vira uma célula com várias
 * linhas, e não uma linha por evidência, porque quem confere quer contar
 * apontamentos, não evidências.</p>
 *
 * <p>{@code Optional.empty()} nas duas listas de valores continua significando
 * o que significa no domínio: em {@code valoresEncontrados}, que o campo não
 * veio no documento; em {@code valoresEsperados}, que a regra não tinha valor de
 * referência a opor. Nenhum dos dois é o mesmo que texto vazio, e a planilha
 * escreve uma marca explícita em vez de deixar a célula em branco.</p>
 */
public record LinhaDeAchado(
        String documentoPseudonimizado,
        String modelo,
        String serie,
        String numero,
        LocalDate dataEmissao,
        Uf ufEmitente,
        int numeroItem,
        String regraId,
        String regraVersao,
        Severidade severidade,
        List<String> campos,
        List<Optional<String>> valoresEncontrados,
        List<Optional<String>> valoresEsperados,
        String fundamentoNormativo,
        LocalDate vigenciaInicio,
        Optional<LocalDate> vigenciaFim,
        Optional<BigDecimal> valorEmRisco,
        Optional<String> motivoDoValorAusente,
        StatusDeTratativa statusDeTratativa,
        Optional<String> justificativaDaTratativa,
        Optional<Instant> tratadoEm) {

    public LinhaDeAchado {
        exigirTexto(documentoPseudonimizado, "documentoPseudonimizado");
        exigirTexto(regraId, "regraId");
        exigirTexto(regraVersao, "regraVersao");
        exigirTexto(fundamentoNormativo, "fundamentoNormativo");

        if (numeroItem < 1) {
            throw new PapelDeTrabalhoInvalido(
                    "O número do item deve ser maior ou igual a 1, mas veio %d.".formatted(numeroItem));
        }
        if (severidade == null || vigenciaInicio == null || statusDeTratativa == null) {
            throw new PapelDeTrabalhoInvalido(
                    "A linha de achado precisa de severidade, vigência aplicada e status de tratativa.");
        }
        if (campos == null || valoresEncontrados == null || valoresEsperados == null) {
            throw new PapelDeTrabalhoInvalido("A linha de achado precisa das evidências.");
        }
        if (campos.isEmpty()) {
            throw new PapelDeTrabalhoInvalido(
                    "A linha de achado precisa de ao menos uma evidência: sem evidência não há o que "
                            + "conferir na planilha.");
        }
        if (campos.size() != valoresEncontrados.size() || campos.size() != valoresEsperados.size()) {
            throw new PapelDeTrabalhoInvalido(
                    ("As evidências vêm em três listas alinhadas posição a posição, mas vieram com %d "
                            + "campos, %d valores encontrados e %d esperados. Desalinhadas, elas fariam a "
                            + "planilha atribuir um valor ao campo errado.")
                            .formatted(campos.size(), valoresEncontrados.size(), valoresEsperados.size()));
        }
        if (campos.stream().anyMatch(campo -> campo == null || campo.isBlank())) {
            throw new PapelDeTrabalhoInvalido("A lista de campos analisados tem entrada em branco.");
        }
        if (valoresEncontrados.stream().anyMatch(Objects::isNull)
                || valoresEsperados.stream().anyMatch(Objects::isNull)) {
            throw new PapelDeTrabalhoInvalido(
                    "Valor ausente se representa com Optional.empty(), nunca com nulo.");
        }

        exigirOptional(vigenciaFim, "vigenciaFim");
        exigirOptional(valorEmRisco, "valorEmRisco");
        exigirOptional(motivoDoValorAusente, "motivoDoValorAusente");
        exigirOptional(justificativaDaTratativa, "justificativaDaTratativa");
        exigirOptional(tratadoEm, "tratadoEm");

        if (valorEmRisco.isEmpty() && motivoDoValorAusente.isEmpty()) {
            throw new PapelDeTrabalhoInvalido(
                    "Sem valor em risco, a linha precisa dizer por que não há. Célula vazia sem "
                            + "explicação é o silêncio que a planilha existe para evitar.");
        }

        campos = List.copyOf(campos);
        valoresEncontrados = List.copyOf(valoresEncontrados);
        valoresEsperados = List.copyOf(valoresEsperados);
    }

    /** Quantidade de evidências desta linha. */
    public int quantidadeDeEvidencias() {
        return campos.size();
    }

    private static void exigirTexto(String valor, String nomeDoCampo) {
        if (valor == null || valor.isBlank()) {
            throw new PapelDeTrabalhoInvalido(
                    "O campo \"%s\" da linha de achado é obrigatório.".formatted(nomeDoCampo));
        }
    }

    private static void exigirOptional(Optional<?> valor, String nomeDoCampo) {
        if (valor == null) {
            throw new PapelDeTrabalhoInvalido(
                    "O campo \"%s\" deve ser Optional.empty() quando não há, nunca nulo."
                            .formatted(nomeDoCampo));
        }
    }
}
