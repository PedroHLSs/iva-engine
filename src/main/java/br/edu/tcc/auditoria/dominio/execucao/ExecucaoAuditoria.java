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

/**
 * Recibo de uma rodada de auditoria.
 *
 * <p>Existe para que um relatório possa ser refeito e conferido depois. Para
 * isso precisa dizer, junto: <em>o que</em> entrou ({@code hashEntrada}, resumo
 * do conjunto de arquivos processados), <em>contra o quê</em> foi confrontado
 * ({@code versaoCatalogo}), <em>com que critério</em>
 * ({@code versaoConjuntoRegras}), <em>quanto</em> passou
 * ({@code quantidadeDocumentos}, {@code quantidadeItens}) e <em>o que saiu</em>
 * (as duas contagens). Faltando qualquer um desses, o número de apontamentos
 * deixa de ser interpretável.</p>
 *
 * <h2>As contagens incluem zero</h2>
 *
 * <p>{@code achadosPorSeveridade} traz as quatro severidades e
 * {@code achadosPorRegra} traz todas as regras aplicadas, mesmo as que não
 * apontaram nada. Omitir a linha zerada obrigaria o leitor a adivinhar se não
 * houve apontamento ou se a regra não rodou — e essa é exatamente a confusão que
 * este projeto evita em toda parte.</p>
 *
 * <h2>Nenhuma contagem é de apontamento filtrado</h2>
 *
 * <p>As contagens vêm dos apontamentos gerados na rodada, antes de qualquer
 * tratativa. Uma execução registra o que a auditoria encontrou; o que se decidiu
 * depois sobre cada apontamento é assunto da tratativa, e não altera o recibo da
 * rodada.</p>
 *
 * @param achadosPorSeveridade quantidade de apontamentos por severidade
 * @param achadosPorRegra      quantidade de apontamentos por identificador de regra
 */
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

    /**
     * Monta o recibo derivando as contagens dos apontamentos da rodada.
     *
     * <p>Preferir esta fábrica ao construtor: aqui as contagens não podem
     * divergir dos apontamentos, porque são calculadas deles.</p>
     *
     * @param regrasAplicadas identificadores de todas as regras do conjunto, para
     *                        que as que nada apontaram apareçam com zero
     */
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

    /** Total de apontamentos da rodada. */
    public int quantidadeDeAchados() {
        return achadosPorSeveridade.values().stream().mapToInt(Integer::intValue).sum();
    }

    /** Quantidade de apontamentos da severidade indicada, zero se não houve nenhum. */
    public int achadosDe(Severidade severidade) {
        return achadosPorSeveridade.getOrDefault(severidade, 0);
    }

    /** Quantidade de apontamentos da regra indicada, zero se não houve nenhum. */
    public int achadosDaRegra(String regraId) {
        return achadosPorRegra.getOrDefault(regraId, 0);
    }

    /** Severidades que a contagem sempre traz, da mais grave para a menos grave. */
    public List<Severidade> severidadesContadas() {
        return List.of(Severidade.values());
    }

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

    private static void exigirTexto(String valor, String nomeDoCampo) {
        if (valor == null || valor.isBlank()) {
            throw new ExecucaoInvalida(
                    ("O campo \"%s\" da execução é obrigatório: sem ele o relatório não é "
                            + "reproduzível.").formatted(nomeDoCampo));
        }
    }

    private static void exigirNaoNegativo(int valor, String nomeDoCampo) {
        if (valor < 0) {
            throw new ExecucaoInvalida(
                    "O campo \"%s\" da execução não pode ser negativo, mas veio %d."
                            .formatted(nomeDoCampo, valor));
        }
    }
}
