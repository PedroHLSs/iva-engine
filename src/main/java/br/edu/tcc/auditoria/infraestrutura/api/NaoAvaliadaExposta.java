package br.edu.tcc.auditoria.infraestrutura.api;

// Representa uma avaliação que o motor não conseguiu concluir, como a API mostra. O resultado NAO_AVALIADO vai escrito por extenso, e o motivo diz o que faltou: campo na nota, tabela no catálogo ou data fora da cobertura.
public record NaoAvaliadaExposta(
        DocumentoExposto documento,
        int numeroItem,
        String regraId,
        String regraNome,
        String motivoDoNomeDaRegraAusente,
        String regraVersao,
        String resultado,
        String motivo) {

    // Valida que haja documento, item, regra com versão, nome da regra ou o motivo de faltar, resultado e motivo.
    public NaoAvaliadaExposta {
        if (documento == null) {
            throw new RespostaInvalida("A avaliação não concluída precisa do documento.");
        }
        if (numeroItem < 1) {
            throw new RespostaInvalida(
                    "O número do item deve ser maior ou igual a 1, mas veio %d.".formatted(numeroItem));
        }
        if (regraId == null || regraId.isBlank() || regraVersao == null || regraVersao.isBlank()) {
            throw new RespostaInvalida(
                    "A avaliação não concluída precisa da regra e da versão dela.");
        }
        NomeDaRegra.exigirPar(regraNome, motivoDoNomeDaRegraAusente, regraId);
        if (resultado == null || resultado.isBlank()) {
            throw new RespostaInvalida(
                    "A avaliação não concluída precisa dizer, por extenso, que o resultado é "
                            + "NAO_AVALIADO. Deixar isso implícito no nome do array faria a distinção "
                            + "morrer no primeiro recorte que alguém copiasse da resposta.");
        }
        if (motivo == null || motivo.isBlank()) {
            throw new RespostaInvalida(
                    "A avaliação não concluída precisa do motivo: é a única coisa que ela tem a dizer, "
                            + "e sem ele a resposta afirma que algo não foi avaliado sem dizer o que "
                            + "faltou.");
        }
    }
}
