package br.edu.tcc.auditoria.dominio;

import br.edu.tcc.auditoria.dominio.excecao.AchadoInvalido;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;

// Representa um apontamento: o problema que uma regra encontrou num item. Guarda a regra e a versão, a nota e o item, as evidências, a base normativa, o período usado e o valor em risco; sem isso não dá para conferir.
public record Achado(
        String regraId,
        String regraVersao,
        Severidade severidade,
        ChaveAcesso chaveAcesso,
        OptionalInt numeroItem,
        List<Evidencia> evidencias,
        String fundamentoNormativo,
        PeriodoVigencia vigenciaAplicada,
        ValorEmRisco valorEmRisco) {

    // Valida o apontamento: exige regra, versão, gravidade, nota, fundamento, período e valor em risco, e pelo menos uma evidência.
    public Achado {
        exigirTexto(regraId, "regraId");
        exigirTexto(regraVersao, "regraVersao");
        exigirPresente(severidade, "severidade");
        exigirPresente(chaveAcesso, "chaveAcesso");
        exigirTexto(fundamentoNormativo, "fundamentoNormativo");
        exigirPresente(vigenciaAplicada, "vigenciaAplicada");
        exigirPresente(valorEmRisco, "valorEmRisco");

        if (numeroItem == null) {
            throw new AchadoInvalido(
                    "Apontamento de documento, sem item, se representa com OptionalInt.empty(), nunca com nulo.");
        }
        if (numeroItem.isPresent() && numeroItem.getAsInt() < 1) {
            throw new AchadoInvalido(
                    "O número do item apontado deve ser maior ou igual a 1, mas veio %d."
                            .formatted(numeroItem.getAsInt()));
        }

        if (evidencias == null) {
            throw new AchadoInvalido("A lista de evidências não pode ser nula.");
        }
        if (evidencias.isEmpty()) {
            throw new AchadoInvalido(
                    "Um apontamento precisa de ao menos uma evidência: sem evidência não há o que conferir.");
        }
        // Confere com stream, e não com List.contains(null), porque lista imutável dá erro quando consultada com nulo.
        if (evidencias.stream().anyMatch(Objects::isNull)) {
            throw new AchadoInvalido("A lista de evidências não pode conter elemento nulo.");
        }
        evidencias = List.copyOf(evidencias);
    }

    // Indica se o apontamento é de um item, e não da nota inteira. Ainda não é usado em produção; existe para ninguém confundir "sem item" com "item zero".
    public boolean ehDeItem() {
        return numeroItem.isPresent();
    }

    // Atalho que devolve o valor em risco, ou vazio quando não dá para calcular; o motivo fica em valorEmRisco().
    public Optional<BigDecimal> quantiaEmRisco() {
        return valorEmRisco.valor();
    }

    // Método auxiliar para verificar se um campo obrigatório é nulo e lançar uma exceção.
    private static void exigirPresente(Object valor, String nomeDoCampo) {
        if (valor == null) {
            throw new AchadoInvalido("O campo \"%s\" do apontamento é obrigatório.".formatted(nomeDoCampo));
        }
    }

    // Método auxiliar para verificar se um campo de texto obrigatório está vazio e lançar uma exceção.
    private static void exigirTexto(String valor, String nomeDoCampo) {
        if (valor == null || valor.isBlank()) {
            throw new AchadoInvalido("O campo \"%s\" do apontamento é obrigatório.".formatted(nomeDoCampo));
        }
    }
}
