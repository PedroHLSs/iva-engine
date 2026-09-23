package br.edu.tcc.auditoria.aplicacao.conferencia;

import br.edu.tcc.auditoria.dominio.Achado;
import br.edu.tcc.auditoria.dominio.ValorEmRisco;

import java.util.List;
import java.util.Objects;

// Interface selada que explica por que uma regra concluiu o que concluiu: por apontamento, por pendência ou por derivação.
public sealed interface ExplicacaoDaVerificacao {

    // Representa a explicação de um apontamento, com as evidências, o fundamento e o valor em risco gravados.
    record PorApontamento(
            List<PassoDeEvidencia> evidencias,
            ReferenciaNormativa fundamentacao,
            ValorEmRisco valorEmRisco) implements ExplicacaoDaVerificacao {

        // Valida que o apontamento tenha evidências, fundamentação e valor em risco.
        public PorApontamento {
            if (evidencias == null || evidencias.isEmpty()) {
                throw new ConferenciaInvalida(
                        "Um apontamento sem evidência não é conferível, e o domínio não permite criar "
                                + "um. Chegar aqui sem nenhuma seria perda no caminho.");
            }
            if (evidencias.stream().anyMatch(Objects::isNull)) {
                throw new ConferenciaInvalida("A lista de evidências não pode conter elemento nulo.");
            }
            if (fundamentacao == null) {
                throw new ConferenciaInvalida(
                        "O apontamento precisa do fundamento e da vigência aplicada: é o que permite "
                                + "conferir se ele ainda procede.");
            }
            if (valorEmRisco == null) {
                throw new ConferenciaInvalida(
                        "O valor em risco vem do apontamento, calculado ou com o motivo de não ser "
                                + "calculável. Nulo aqui apagaria a diferença.");
            }
            evidencias = List.copyOf(evidencias);
        }

        // Método estático que monta a explicação a partir do que o apontamento gravou.
        public static PorApontamento de(Achado achado) {
            if (achado == null) {
                throw new ConferenciaInvalida("Não há apontamento a explicar.");
            }
            return new PorApontamento(
                    achado.evidencias().stream().map(PassoDeEvidencia::de).toList(),
                    new ReferenciaNormativa(
                            achado.fundamentoNormativo(),
                            achado.vigenciaAplicada().inicio(),
                            achado.vigenciaAplicada().fim()),
                    achado.valorEmRisco());
        }
    }

    // Representa a explicação de uma pendência, com o motivo que a própria regra escreveu, sem edição.
    record PorPendencia(String motivo) implements ExplicacaoDaVerificacao {

        // Valida que a pendência traga o motivo.
        public PorPendencia {
            if (motivo == null || motivo.isBlank()) {
                throw new ConferenciaInvalida(
                        "Uma verificação sem conclusão precisa do motivo. Sem ele a tela diria que algo "
                                + "não foi avaliado sem dizer o que faltou, que é o defeito que esta "
                                + "etapa existe para não repetir.");
            }
        }
    }

    // Representa a explicação de um resultado sem violação, derivado por conta; não diz "conferido".
    record PorDerivacao(String conta) implements ExplicacaoDaVerificacao {

        // Valida que a derivação diga como foi feita.
        public PorDerivacao {
            if (conta == null || conta.isBlank()) {
                throw new ConferenciaInvalida("A derivação precisa dizer como foi feita.");
            }
        }

        // Método estático que escreve por extenso a conta que derivou o resultado da regra indicada.
        public static PorDerivacao daRegra(String regraId) {
            if (regraId == null || regraId.isBlank()) {
                throw new ConferenciaInvalida("Não há regra cuja derivação explicar.");
            }
            return new PorDerivacao(
                    ("A regra %s foi aplicada nesta análise e não deixou apontamento nem avaliação não "
                            + "concluída para este item. Como o motor produz exatamente uma avaliação "
                            + "por par de item e regra, a subtração é exata: ela concluiu sem encontrar "
                            + "violação. Não é estimativa, e também não é conferência — é o que as "
                            + "regras cadastradas alcançam.").formatted(regraId));
        }
    }
}
