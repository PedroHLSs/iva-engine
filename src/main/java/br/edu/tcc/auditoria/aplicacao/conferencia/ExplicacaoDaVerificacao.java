package br.edu.tcc.auditoria.aplicacao.conferencia;

import br.edu.tcc.auditoria.dominio.Achado;
import br.edu.tcc.auditoria.dominio.ValorEmRisco;

import java.util.List;
import java.util.Objects;

/**
 * Por que uma regra concluiu o que concluiu sobre este produto.
 *
 * <h2>Três variantes porque há três procedências, e não três estilos de texto</h2>
 *
 * <p>Um apontamento foi gravado com evidências, fundamento e vigência: a
 * explicação dele é o que ele carrega. Uma pendência foi gravada com o motivo que
 * a própria regra escreveu ao desistir: a explicação dela é esse motivo. Um
 * conforme não foi gravado de jeito nenhum — o banco não guarda avaliação
 * conforme (D009) —, e a explicação dele é a conta que o derivou.</p>
 *
 * <p>Escrever os três com a mesma frase genérica seria a forma mais fácil de a
 * tela parecer completa sem dizer nada. O tipo é selado justamente para que um
 * quarto caso não possa ser acrescentado sem que se decida o que ele explica.</p>
 */
public sealed interface ExplicacaoDaVerificacao {

    /** A regra apontou, e estas são as evidências que ela gravou. */
    record PorApontamento(
            List<PassoDeEvidencia> evidencias,
            ReferenciaNormativa fundamentacao,
            ValorEmRisco valorEmRisco) implements ExplicacaoDaVerificacao {

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

        /** Monta a explicação a partir do que o apontamento gravou. */
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

    /**
     * A regra não concluiu, e este é o motivo que ela escreveu.
     *
     * <p>O texto é o da regra, repetido sem edição. Ele é quem diz se faltou
     * campo no documento, se faltou tabela no catálogo ou se a data ficou fora da
     * cobertura declarada — três problemas de donos diferentes, e a diferença é
     * toda a utilidade da pendência.</p>
     */
    record PorPendencia(String motivo) implements ExplicacaoDaVerificacao {

        public PorPendencia {
            if (motivo == null || motivo.isBlank()) {
                throw new ConferenciaInvalida(
                        "Uma verificação sem conclusão precisa do motivo. Sem ele a tela diria que algo "
                                + "não foi avaliado sem dizer o que faltou, que é o defeito que esta "
                                + "etapa existe para não repetir.");
            }
        }
    }

    /**
     * A regra concluiu sem encontrar violação, e este resultado foi derivado.
     *
     * <p>A frase diz a conta, e diz que é conta. <strong>Não diz "conferido"</strong>:
     * o sistema não conferiu nada, aplicou as regras cadastradas sobre os campos
     * que elas alcançam e nenhuma delas encontrou violação. São afirmações
     * diferentes, e a segunda é a verdadeira.</p>
     */
    record PorDerivacao(String conta) implements ExplicacaoDaVerificacao {

        public PorDerivacao {
            if (conta == null || conta.isBlank()) {
                throw new ConferenciaInvalida("A derivação precisa dizer como foi feita.");
            }
        }

        /** A conta, escrita por extenso, para a regra indicada. */
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
