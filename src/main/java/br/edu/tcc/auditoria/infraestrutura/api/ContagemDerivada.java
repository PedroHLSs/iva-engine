package br.edu.tcc.auditoria.infraestrutura.api;

/**
 * Uma contagem que o sistema não gravou e que esta camada calcula, dizendo como.
 *
 * <h2>Por que existe</h2>
 *
 * <p>O banco não guarda avaliação conforme. A Etapa 5 grava apontamento, a
 * Etapa 6 acrescentou as não concluídas, e a regra que se aplicou por inteiro e
 * nada encontrou não deixa linha nenhuma — é justamente por isso que o harness
 * de acurácia roda o motor de novo em vez de ler o banco (D008).</p>
 *
 * <p>Omitir o conforme da resposta faria o consumidor concluir "não está em
 * achados nem em não avaliados, logo está conforme", que é o colapso dos três
 * desfechos que a Etapa 8 existe para impedir. Então ele aparece, calculado, com
 * a conta impressa em {@code derivacao} — e nunca sem ela.</p>
 *
 * <h2>A derivação é exata, não é estimativa</h2>
 *
 * <p>{@code MotorAuditoria} produz exatamente uma avaliação por par
 * (item, regra), toda avaliação com achado está gravada em {@code achado} mais
 * {@code achado_da_execucao}, e toda avaliação não concluída está em
 * {@code avaliacao_nao_concluida}. Logo a subtração fecha.</p>
 *
 * <p>Se ela der negativo, os dados gravados se contradizem — banco alterado por
 * fora, tipicamente. Nesse caso {@code valor} é {@code null} e
 * {@code motivoDaAusencia} diz o que não fechou. Nunca zero: zero afirmaria que
 * nenhum item está conforme, o que é uma afirmação sobre o acervo, e não sobre o
 * banco.</p>
 *
 * @param valor            a contagem, ou {@code null} quando não é derivável
 * @param derivacao        a conta que produziu o valor, ou {@code null} quando não houve
 * @param motivoDaAusencia por que não há valor, ou {@code null} quando há
 */
public record ContagemDerivada(Long valor, String derivacao, String motivoDaAusencia) {

    public ContagemDerivada {
        if (valor == null && (motivoDaAusencia == null || motivoDaAusencia.isBlank())) {
            throw new RespostaInvalida(
                    "Contagem derivada sem valor precisa dizer por que não há. null sem motivo é "
                            + "indistinguível de zero para quem lê a resposta.");
        }
        if (valor != null && motivoDaAusencia != null) {
            throw new RespostaInvalida(
                    "Contagem derivada não pode ter valor e motivo da ausência ao mesmo tempo: "
                            + "veio %d e \"%s\".".formatted(valor, motivoDaAusencia));
        }
        if (valor != null && (derivacao == null || derivacao.isBlank())) {
            throw new RespostaInvalida(
                    "Contagem derivada precisa mostrar a conta que a produziu: número sem procedência "
                            + "numa auditoria é número que ninguém confere.");
        }
        if (valor != null && valor < 0) {
            throw new RespostaInvalida(
                    ("Contagem derivada negativa (%d) não é resposta, é erro de contagem. Use "
                            + "ContagemDerivada.naoDerivavel(motivo).").formatted(valor));
        }
    }

    /**
     * Deriva {@code total - achado - naoAvaliado}, ou recusa dizendo o que não
     * fechou.
     *
     * @param oQueEOTotal como o total foi obtido, para entrar na conta impressa
     */
    public static ContagemDerivada conformesDe(
            long total, long achado, long naoAvaliado, String oQueEOTotal) {

        long conformes = total - achado - naoAvaliado;
        String conta = "%s %d - achado %d - naoAvaliado %d"
                .formatted(oQueEOTotal, total, achado, naoAvaliado);

        if (conformes < 0) {
            return naoDerivavel(
                    ("a conta não fecha: %s daria %d. Há mais avaliações gravadas do que o motor "
                            + "poderia ter produzido, o que significa que os dados gravados se "
                            + "contradizem. O conforme não é reportado como zero porque zero seria uma "
                            + "afirmação sobre o acervo, e o problema está no banco.")
                            .formatted(conta, conformes));
        }
        return new ContagemDerivada(conformes, conta, null);
    }

    public static ContagemDerivada naoDerivavel(String motivo) {
        return new ContagemDerivada(null, null, motivo);
    }
}
