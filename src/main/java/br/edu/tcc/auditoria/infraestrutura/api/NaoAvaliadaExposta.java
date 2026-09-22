package br.edu.tcc.auditoria.infraestrutura.api;

/**
 * Uma avaliação que o motor não conseguiu concluir, como a API a mostra.
 *
 * <p>Não é apontamento e não é conformidade: é a terceira coisa. Carrega
 * {@code "resultado": "NAO_AVALIADO"} escrito por extenso, pelo mesmo motivo que
 * {@link AchadoExposto} carrega {@code "ACHADO"} — a distinção não pode depender
 * de em qual array o consumidor olhou.</p>
 *
 * <p>O {@code motivo} é o texto que a própria regra escreveu ao desistir. Ele diz
 * se faltou campo no documento, se faltou tabela no catálogo, ou se a data ficou
 * fora da cobertura declarada da carga — três problemas de donos diferentes, e é
 * por isso que a contagem sozinha não bastaria (D004).</p>
 *
 * <p>Diferente do apontamento, estas linhas não são deduplicadas entre execuções:
 * não concluir é fato da rodada, não do documento. A mesma regra sobre o mesmo
 * item pode não concluir hoje por falta de tabela e concluir amanhã (D007).</p>
 *
 * <p>{@code regraNome} e {@code motivoDoNomeDaRegraAusente} foram acrescentados
 * depois da Etapa 11, para a interface não escrever só o código: ver
 * {@link NomeDaRegra}.</p>
 */
public record NaoAvaliadaExposta(
        DocumentoExposto documento,
        int numeroItem,
        String regraId,
        String regraNome,
        String motivoDoNomeDaRegraAusente,
        String regraVersao,
        String resultado,
        String motivo) {

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
