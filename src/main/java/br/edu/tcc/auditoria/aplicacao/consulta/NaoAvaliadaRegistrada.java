package br.edu.tcc.auditoria.aplicacao.consulta;

import br.edu.tcc.auditoria.dominio.ChaveAcesso;

// Representa uma avaliação que não concluiu, como foi gravada, com o motivo que a própria regra escreveu.
public record NaoAvaliadaRegistrada(
        ChaveAcesso chaveAcesso,
        int numeroItem,
        String regraId,
        String regraVersao,
        String motivo) {

    // Valida que a avaliação tenha chave de acesso, número do item, regra, versão e motivo.
    public NaoAvaliadaRegistrada {
        if (chaveAcesso == null) {
            throw new ConsultaInvalida("A avaliação não concluída precisa da chave de acesso.");
        }
        if (numeroItem < 1) {
            throw new ConsultaInvalida(
                    "O número do item deve ser maior ou igual a 1, mas veio %d.".formatted(numeroItem));
        }
        if (regraId == null || regraId.isBlank()) {
            throw new ConsultaInvalida("A avaliação não concluída precisa dizer de que regra veio.");
        }
        if (regraVersao == null || regraVersao.isBlank()) {
            throw new ConsultaInvalida("A avaliação não concluída precisa da versão da regra.");
        }
        if (motivo == null || motivo.isBlank()) {
            throw new ConsultaInvalida(
                    "A avaliação não concluída precisa do motivo: sem ele o relatório diz que algo não "
                            + "foi avaliado sem dizer o que faltou.");
        }
    }
}
