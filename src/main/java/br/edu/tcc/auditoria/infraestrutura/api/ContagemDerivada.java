package br.edu.tcc.auditoria.infraestrutura.api;

// Representa uma contagem que o banco não guarda e que é calculada aqui, com a conta escrita ao lado. Serve para os conformes, que o banco não grava; se a conta não fechar, o valor vem null com o motivo, e nunca zero.
public record ContagemDerivada(Long valor, String derivacao, String motivoDaAusencia) {

    // Valida que haja valor ou motivo, nunca os dois, que o valor venha com a conta e que não seja negativo.
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

    // Método estático que calcula os conformes como total - achados - não avaliados; se der negativo, devolve sem valor e com o motivo.
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

    // Método estático que cria a contagem sem valor, com o motivo.
    public static ContagemDerivada naoDerivavel(String motivo) {
        return new ContagemDerivada(null, null, motivo);
    }
}
