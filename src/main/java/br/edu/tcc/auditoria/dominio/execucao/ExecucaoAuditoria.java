package br.edu.tcc.auditoria.dominio.execucao;

import br.edu.tcc.auditoria.dominio.Achado;
import br.edu.tcc.auditoria.dominio.Severidade;
import br.edu.tcc.auditoria.dominio.excecao.ExecucaoInvalida;

import java.time.Instant;
import java.util.Collection;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

// Representa o recibo de uma rodada de auditoria, com o que entrou, as versões de catálogo e de regras, as quantidades e as contagens de apontamentos, zero incluído e antes de qualquer tratativa.
public record ExecucaoAuditoria(
        UUID id,
        Instant dataHora,
        String hashEntrada,
        String versaoCatalogo,
        String versaoConjuntoRegras,
        int quantidadeDocumentos,
        int quantidadeItens,
        Map<Severidade, Integer> achadosPorSeveridade,
        Map<String, Integer> achadosPorRegra) {

    // Valida o recibo: exige identificador, data, resumo da entrada e as duas versões, recusa quantidade negativa e copia as contagens.
    public ExecucaoAuditoria {
        if (id == null) {
            throw new ExecucaoInvalida("A execução precisa de identificador.");
        }
        if (dataHora == null) {
            throw new ExecucaoInvalida("A execução precisa registrar quando rodou.");
        }
        exigirTexto(hashEntrada, "hashEntrada");
        exigirTexto(versaoCatalogo, "versaoCatalogo");
        exigirTexto(versaoConjuntoRegras, "versaoConjuntoRegras");
        exigirNaoNegativo(quantidadeDocumentos, "quantidadeDocumentos");
        exigirNaoNegativo(quantidadeItens, "quantidadeItens");

        achadosPorSeveridade = copiarContagemPorSeveridade(achadosPorSeveridade);
        achadosPorRegra = copiarContagemPorRegra(achadosPorRegra);
    }

    // Método estático que monta o recibo calculando as contagens a partir dos apontamentos, com zero nas regras que nada apontaram; preferível ao construtor.
    public static ExecucaoAuditoria de(
            UUID id,
            Instant dataHora,
            String hashEntrada,
            String versaoCatalogo,
            String versaoConjuntoRegras,
            int quantidadeDocumentos,
            int quantidadeItens,
            Collection<String> regrasAplicadas,
            Collection<Achado> achados) {

        if (regrasAplicadas == null) {
            throw new ExecucaoInvalida(
                    "A execução precisa saber quais regras foram aplicadas para contar zero nas que não "
                            + "apontaram nada.");
        }
        if (achados == null) {
            throw new ExecucaoInvalida(
                    "A lista de apontamentos deve ser vazia quando não houve nenhum, nunca nula.");
        }
        if (achados.stream().anyMatch(Objects::isNull)) {
            throw new ExecucaoInvalida("A lista de apontamentos não pode conter elemento nulo.");
        }

        Map<Severidade, Integer> porSeveridade = new EnumMap<>(Severidade.class);
        for (Severidade severidade : Severidade.values()) {
            porSeveridade.put(severidade, 0);
        }
        Map<String, Integer> porRegra = new LinkedHashMap<>();
        for (String regraId : regrasAplicadas) {
            if (regraId == null || regraId.isBlank()) {
                throw new ExecucaoInvalida("A lista de regras aplicadas tem identificador em branco.");
            }
            porRegra.put(regraId, 0);
        }

        for (Achado achado : achados) {
            porSeveridade.merge(achado.severidade(), 1, Integer::sum);
            porRegra.merge(achado.regraId(), 1, Integer::sum);
        }

        return new ExecucaoAuditoria(
                id,
                dataHora,
                hashEntrada,
                versaoCatalogo,
                versaoConjuntoRegras,
                quantidadeDocumentos,
                quantidadeItens,
                porSeveridade,
                porRegra);
    }

    // Retorna o total de apontamentos da rodada.
    public int quantidadeDeAchados() {
        return achadosPorSeveridade.values().stream().mapToInt(Integer::intValue).sum();
    }

    // Retorna a quantidade de apontamentos da severidade indicada, ou zero se não houve nenhum.
    public int achadosDe(Severidade severidade) {
        return achadosPorSeveridade.getOrDefault(severidade, 0);
    }

    // Retorna a quantidade de apontamentos da regra indicada, ou zero se não houve nenhum.
    public int achadosDaRegra(String regraId) {
        return achadosPorRegra.getOrDefault(regraId, 0);
    }

    // Retorna as severidades que a contagem sempre traz, da mais grave para a menos grave.
    public List<Severidade> severidadesContadas() {
        return List.of(Severidade.values());
    }

    // Método auxiliar que valida e copia a contagem por severidade.
    private static Map<Severidade, Integer> copiarContagemPorSeveridade(Map<Severidade, Integer> contagem) {
        if (contagem == null) {
            throw new ExecucaoInvalida(
                    "A contagem por severidade deve trazer zero onde não houve apontamento, nunca ser nula.");
        }
        Map<Severidade, Integer> copia = new EnumMap<>(Severidade.class);
        contagem.forEach((severidade, quantidade) -> {
            if (severidade == null) {
                throw new ExecucaoInvalida("A contagem por severidade não pode ter severidade nula.");
            }
            copia.put(severidade, validarQuantidade(quantidade, severidade.name()));
        });
        return Map.copyOf(copia);
    }

    // Método auxiliar que valida e copia a contagem por regra.
    private static Map<String, Integer> copiarContagemPorRegra(Map<String, Integer> contagem) {
        if (contagem == null) {
            throw new ExecucaoInvalida(
                    "A contagem por regra deve trazer zero onde a regra não apontou nada, nunca ser nula.");
        }
        Map<String, Integer> copia = new LinkedHashMap<>();
        contagem.forEach((regraId, quantidade) -> {
            if (regraId == null || regraId.isBlank()) {
                throw new ExecucaoInvalida("A contagem por regra não pode ter identificador em branco.");
            }
            copia.put(regraId, validarQuantidade(quantidade, regraId));
        });
        return Map.copyOf(copia);
    }

    // Método auxiliar que recusa quantidade nula ou negativa; ausência de apontamento é zero, não nulo.
    private static int validarQuantidade(Integer quantidade, String chave) {
        if (quantidade == null) {
            throw new ExecucaoInvalida(
                    "A contagem de \"%s\" veio nula. Ausência de apontamento é zero, não nulo."
                            .formatted(chave));
        }
        if (quantidade < 0) {
            throw new ExecucaoInvalida(
                    "A contagem de \"%s\" veio negativa: %d.".formatted(chave, quantidade));
        }
        return quantidade;
    }

    // Método auxiliar para verificar se um campo de texto obrigatório está vazio e lançar uma exceção.
    private static void exigirTexto(String valor, String nomeDoCampo) {
        if (valor == null || valor.isBlank()) {
            throw new ExecucaoInvalida(
                    ("O campo \"%s\" da execução é obrigatório: sem ele o relatório não é "
                            + "reproduzível.").formatted(nomeDoCampo));
        }
    }

    // Método auxiliar para verificar se uma quantidade é negativa e lançar uma exceção.
    private static void exigirNaoNegativo(int valor, String nomeDoCampo) {
        if (valor < 0) {
            throw new ExecucaoInvalida(
                    "O campo \"%s\" da execução não pode ser negativo, mas veio %d."
                            .formatted(nomeDoCampo, valor));
        }
    }
}
