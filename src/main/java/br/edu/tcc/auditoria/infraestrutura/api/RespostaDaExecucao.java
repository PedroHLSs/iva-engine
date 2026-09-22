package br.edu.tcc.auditoria.infraestrutura.api;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * A execução completa: identificação da rodada, os três desfechos, e o que cada
 * regra produziu.
 *
 * <h2>Os três desfechos, sem colapso e sem subtração do consumidor</h2>
 *
 * <p>É a propriedade central desta etapa. {@code ACHADO} e {@code NAO_AVALIADO}
 * são campos próprios, escritos sempre — inclusive em zero —, e o
 * {@code NAO_AVALIADO} vem acompanhado dos motivos agrupados, porque a contagem
 * sozinha não diz se faltou campo no documento, tabela no catálogo ou cobertura na
 * carga (D004). {@code CONFORME} é derivado, e diz que é: ver
 * {@link ContagemDerivada}.</p>
 *
 * <p>Nenhum dos três se deduz dos outros dois. Quem lê não precisa saber que "o
 * que não é achado nem não avaliado está conforme" — que é exatamente a
 * equivalência falsa que o sistema inteiro existe para não fazer.</p>
 *
 * @param itensComAvaliacaoNaoConcluida quantos itens distintos tiveram ao menos
 *                                      uma regra que não concluiu; é menor que a
 *                                      soma de {@code naoAvaliado} sempre que
 *                                      mais de uma regra desistiu do mesmo item
 */
public record RespostaDaExecucao(
        String id,
        Instant dataHora,
        String hashEntrada,
        String versaoCatalogo,
        String versaoConjuntoRegras,
        int quantidadeDocumentos,
        int quantidadeItens,
        Map<String, Integer> achadosPorSeveridade,
        Desfechos desfechos,
        List<PorRegra> porRegra,
        int itensComAvaliacaoNaoConcluida) {

    public RespostaDaExecucao {
        if (id == null || dataHora == null) {
            throw new RespostaInvalida("A execução precisa de identificador e data e hora.");
        }
        if (desfechos == null) {
            throw new RespostaInvalida(
                    "A execução precisa dos três desfechos. É o que esta resposta existe para dizer.");
        }
        if (porRegra == null || porRegra.isEmpty()) {
            throw new RespostaInvalida(
                    "A execução precisa de uma linha por regra aplicada, inclusive as que não "
                            + "apontaram nada: regra que rodou e nada encontrou não pode sumir do "
                            + "relatório.");
        }
        if (achadosPorSeveridade == null || achadosPorSeveridade.isEmpty()) {
            throw new RespostaInvalida(
                    "A contagem por severidade precisa trazer zero onde não houve apontamento.");
        }
        if (itensComAvaliacaoNaoConcluida < 0) {
            throw new RespostaInvalida(
                    "A contagem de itens com avaliação não concluída não pode ser negativa.");
        }

        int achadoSomado = porRegra.stream().mapToInt(PorRegra::achado).sum();
        int naoAvaliadoSomado = porRegra.stream().mapToInt(PorRegra::naoAvaliado).sum();
        if (achadoSomado != desfechos.achado() || naoAvaliadoSomado != desfechos.naoAvaliado()) {
            throw new RespostaInvalida(
                    ("As linhas por regra somam %d achado(s) e %d não avaliada(s), e o total diz %d e "
                            + "%d. Uma resposta internamente contraditória é pior que nenhuma.")
                            .formatted(
                                    achadoSomado,
                                    naoAvaliadoSomado,
                                    desfechos.achado(),
                                    desfechos.naoAvaliado()));
        }
        porRegra = List.copyOf(porRegra);
    }

    /**
     * Os três desfechos da rodada inteira.
     *
     * @param avaliacoesProduzidas quantas avaliações o motor produziu
     * @param comoFoiObtido        como {@code avaliacoesProduzidas} foi calculado,
     *                             porque o número não está gravado em coluna
     *                             nenhuma e um total sem procedência não se confere
     */
    public record Desfechos(
            long avaliacoesProduzidas,
            String comoFoiObtido,
            int achado,
            int naoAvaliado,
            ContagemDerivada conforme) {

        public Desfechos {
            if (comoFoiObtido == null || comoFoiObtido.isBlank()) {
                throw new RespostaInvalida(
                        "O total de avaliações precisa dizer como foi obtido: ele não está gravado em "
                                + "coluna nenhuma, é calculado aqui.");
            }
            if (conforme == null) {
                throw new RespostaInvalida(
                        "O conforme precisa aparecer, ainda que não derivável. Omiti-lo faria o "
                                + "consumidor concluir por subtração que tudo o que não é achado nem "
                                + "não avaliado está conforme.");
            }
            if (achado < 0 || naoAvaliado < 0 || avaliacoesProduzidas < 0) {
                throw new RespostaInvalida("Contagem de desfecho não pode ser negativa.");
            }
        }
    }

    /**
     * O que uma regra produziu nesta execução.
     *
     * <p>Toda regra aplicada tem linha, inclusive a que não apontou nada e a que
     * concluiu tudo — com {@code achado} e {@code naoAvaliado} em zero e
     * {@code motivosDoNaoAvaliado} vazio, escritos.</p>
     *
     * <p>{@code regraNome} e {@code motivoDoNomeDaRegraAusente} foram acrescentados
     * depois da Etapa 11, para a interface não escrever só o código: ver
     * {@link NomeDaRegra}.</p>
     */
    public record PorRegra(
            String regraId,
            String regraNome,
            String motivoDoNomeDaRegraAusente,
            int achado,
            int naoAvaliado,
            ContagemDerivada conforme,
            List<MotivoDoNaoAvaliado> motivosDoNaoAvaliado) {

        public PorRegra {
            if (regraId == null || regraId.isBlank()) {
                throw new RespostaInvalida("A linha por regra precisa do identificador da regra.");
            }
            NomeDaRegra.exigirPar(regraNome, motivoDoNomeDaRegraAusente, regraId);
            if (conforme == null) {
                throw new RespostaInvalida(
                        "A linha da regra %s precisa do conforme, ainda que não derivável."
                                .formatted(regraId));
            }
            if (motivosDoNaoAvaliado == null) {
                throw new RespostaInvalida(
                        ("A lista de motivos da regra %s deve ser vazia quando a regra concluiu tudo, "
                                + "nunca nula.").formatted(regraId));
            }
            if (achado < 0 || naoAvaliado < 0) {
                throw new RespostaInvalida("Contagem de desfecho não pode ser negativa.");
            }

            int somaDosMotivos = motivosDoNaoAvaliado.stream()
                    .mapToInt(MotivoDoNaoAvaliado::quantidade)
                    .sum();
            if (somaDosMotivos != naoAvaliado) {
                throw new RespostaInvalida(
                        ("Os motivos da regra %s somam %d e a contagem de não avaliadas diz %d. O "
                                + "resumo não pode divergir do detalhe.")
                                .formatted(regraId, somaDosMotivos, naoAvaliado));
            }
            motivosDoNaoAvaliado = List.copyOf(motivosDoNaoAvaliado);
        }
    }

    /**
     * Um motivo de não conclusão, com quantas vezes apareceu.
     *
     * <p>O texto é o que a própria regra escreveu. O agrupamento vem do montador
     * do papel de trabalho, e não de uma segunda implementação aqui: a API e a
     * planilha da mesma execução contam a mesma coisa por construção.</p>
     */
    public record MotivoDoNaoAvaliado(String motivo, int quantidade) {

        public MotivoDoNaoAvaliado {
            if (motivo == null || motivo.isBlank()) {
                throw new RespostaInvalida("O motivo agrupado precisa do texto do motivo.");
            }
            if (quantidade < 1) {
                throw new RespostaInvalida(
                        "Um motivo com quantidade %d não deveria estar na lista.".formatted(quantidade));
            }
        }
    }
}
