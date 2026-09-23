package br.edu.tcc.auditoria.aplicacao.conferencia;

import br.edu.tcc.auditoria.dominio.Achado;
import br.edu.tcc.auditoria.dominio.ResultadoAvaliacao;
import br.edu.tcc.auditoria.dominio.Severidade;
import br.edu.tcc.auditoria.dominio.regras.Avaliacao;

import java.util.Optional;

// Classe que traduz o desfecho do motor no estado da interface: CONFORME e NAO_AVALIADO têm estado próprio, e ACHADO depende da severidade, nunca do identificador da regra.
public final class TraducaoDeDesfecho {

    // Construtor privado: a classe só tem métodos estáticos.
    private TraducaoDeDesfecho() {
    }

    // Traduz o resultado e a severidade no estado da interface; combinação impossível é recusada.
    public static EstadoDeConferencia de(
            ResultadoAvaliacao resultado, Optional<Severidade> severidade) {

        if (resultado == null) {
            throw new ConferenciaInvalida(
                    "Não há desfecho a traduzir. Sem resultado do motor não há estado de interface, e "
                            + "escolher um seria a camada de apresentação inventando auditoria.");
        }
        if (severidade == null) {
            throw new ConferenciaInvalida(
                    "Desfecho sem apontamento se representa com Optional.empty() na severidade, nunca "
                            + "com nulo.");
        }

        return switch (resultado) {
            case ACHADO -> deApontamento(exigirSeveridade(severidade));
            case CONFORME -> {
                exigirSemSeveridade(severidade, ResultadoAvaliacao.CONFORME);
                yield EstadoDeConferencia.SEM_DIVERGENCIA_IDENTIFICADA;
            }
            case NAO_AVALIADO -> {
                exigirSemSeveridade(severidade, ResultadoAvaliacao.NAO_AVALIADO);
                yield EstadoDeConferencia.NAO_FOI_POSSIVEL_CONCLUIR;
            }
        };
    }

    // Traduz uma avaliação do motor, tirando a severidade do próprio apontamento.
    public static EstadoDeConferencia de(Avaliacao avaliacao) {
        if (avaliacao == null) {
            throw new ConferenciaInvalida("Não há avaliação a traduzir.");
        }
        return de(avaliacao.resultado(), avaliacao.achado().map(Achado::severidade));
    }

    // Método auxiliar que traduz a severidade do apontamento; o switch não tem default para quebrar a compilação se surgir severidade nova.
    private static EstadoDeConferencia deApontamento(Severidade severidade) {
        return switch (severidade) {
            case CRITICA, GRAVE, MODERADA -> EstadoDeConferencia.POSSIVEL_DIVERGENCIA;
            case INFORMATIVA -> EstadoDeConferencia.REQUER_CONFERENCIA;
        };
    }

    // Método auxiliar que exige severidade quando houve apontamento.
    private static Severidade exigirSeveridade(Optional<Severidade> severidade) {
        return severidade.orElseThrow(() -> new ConferenciaInvalida(
                "Um apontamento sem severidade não é traduzível: é ela que separa a divergência da "
                        + "situação que só pede leitura humana. Todo Achado carrega a sua."));
    }

    // Método auxiliar que recusa severidade quando não houve apontamento.
    private static void exigirSemSeveridade(
            Optional<Severidade> severidade, ResultadoAvaliacao resultado) {

        severidade.ifPresent(grau -> {
            throw new ConferenciaInvalida(
                    ("O desfecho %s veio com severidade %s. Severidade é atributo de apontamento, e "
                            + "aceitar uma aqui deixaria a tradução depender de um campo que o motor "
                            + "não produziu.").formatted(resultado, grau));
        });
    }
}
