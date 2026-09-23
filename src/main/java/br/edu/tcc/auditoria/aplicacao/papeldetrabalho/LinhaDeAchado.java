package br.edu.tcc.auditoria.aplicacao.papeldetrabalho;

import br.edu.tcc.auditoria.dominio.Severidade;
import br.edu.tcc.auditoria.dominio.Uf;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

// Representa uma linha da aba de achados, identificada pelo pseudônimo da chave, com as evidências em três listas alinhadas posição a posição.
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

    // Valida a linha: exige os campos obrigatórios, evidências alinhadas e, sem valor em risco, o motivo da ausência.
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

    public int quantidadeDeEvidencias() {
        return campos.size();
    }

    // Método auxiliar para verificar se um campo de texto obrigatório está vazio e lançar uma exceção.
    private static void exigirTexto(String valor, String nomeDoCampo) {
        if (valor == null || valor.isBlank()) {
            throw new PapelDeTrabalhoInvalido(
                    "O campo \"%s\" da linha de achado é obrigatório.".formatted(nomeDoCampo));
        }
    }

    // Método auxiliar para verificar se um campo opcional é nulo e lançar uma exceção.
    private static void exigirOptional(Optional<?> valor, String nomeDoCampo) {
        if (valor == null) {
            throw new PapelDeTrabalhoInvalido(
                    "O campo \"%s\" deve ser Optional.empty() quando não há, nunca nulo."
                            .formatted(nomeDoCampo));
        }
    }
}
