package br.edu.tcc.auditoria.infraestrutura.api;

/**
 * O aviso que acompanha toda resposta de resultado.
 *
 * <h2>Por que ele viaja no corpo, e não fica só no HTML</h2>
 *
 * <p>O pedido era que o aviso estivesse em <em>toda</em> tela de resultado. Se
 * ele morasse no JavaScript, cada tela nova precisaria lembrar de escrevê-lo, e
 * a que esquecesse não quebraria nada — ficaria só sem aviso, em silêncio, que é
 * o modo de falha mais provável e o menos visível.</p>
 *
 * <p>Vindo no corpo, ele chega junto do resultado por construção. E chega com o
 * mesmo texto em todas: uma frase só, num lugar só.</p>
 *
 * <p>O texto não é jurídico e não foi copiado de lugar nenhum. Ele descreve o
 * que a ferramenta faz e o que ela não faz — que é o mesmo limite já escrito no
 * objetivo do sistema e na disciplina dos quatro estados.</p>
 */
final class AvisoDeUso {

    static final String TEXTO =
            "Esta análise apoia a conferência fiscal. Ela se baseia nos dados do documento enviado e "
                    + "na base normativa cadastrada, alcança apenas o que as regras cadastradas "
                    + "examinam, não substitui a avaliação de profissional tributário e não constitui "
                    + "parecer.";

    private AvisoDeUso() {
        // Só texto.
    }
}
