package br.edu.tcc.auditoria.infraestrutura.api;

import java.time.Instant;
import java.util.List;
import java.util.Map;

// Representa uma execução completa: identificação, os três resultados e o que cada regra produziu. ACHADO e NAO_AVALIADO vêm sempre, até em zero, e CONFORME é calculado, com a conta ao lado, para ninguém ter de deduzir um resultado dos outros dois.
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

    // Valida a execução: exige identificador, data e hora, resultados, uma linha por regra e contagem por gravidade, e confere que a soma das regras bate com o total.
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

    // Representa os três resultados da execução inteira, com o total de avaliações e como ele foi calculado.
    public record Desfechos(
            long avaliacoesProduzidas,
            String comoFoiObtido,
            int achado,
            int naoAvaliado,
            ContagemDerivada conforme) {

        // Valida que haja a explicação do total e o conforme, e que nenhuma contagem seja negativa.
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

    // Representa o que uma regra produziu na execução. Toda regra aplicada tem linha, até a que não apontou nada.
    public record PorRegra(
            String regraId,
            String regraNome,
            String motivoDoNomeDaRegraAusente,
            int achado,
            int naoAvaliado,
            ContagemDerivada conforme,
            List<MotivoDoNaoAvaliado> motivosDoNaoAvaliado) {

        // Valida a linha: exige regra, nome ou motivo, conforme e lista de motivos, e confere que os motivos somam o total de não avaliadas.
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

    // Representa um motivo de não avaliação e quantas vezes apareceu. O agrupamento é o mesmo do papel de trabalho, para a API e a planilha contarem igual.
    public record MotivoDoNaoAvaliado(String motivo, int quantidade) {

        // Valida que haja o texto do motivo e quantidade de pelo menos 1.
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
