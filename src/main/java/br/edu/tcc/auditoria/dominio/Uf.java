package br.edu.tcc.auditoria.dominio;

import br.edu.tcc.auditoria.dominio.excecao.UfInvalida;

// Enum com as 27 unidades federativas (26 estados e o DF). É divisão do país, sem alíquota nem código ligado; destinatário no exterior fica com a UF vazia.
public enum Uf {

    AC, AL, AM, AP, BA, CE, DF, ES, GO,
    MA, MG, MS, MT, PA, PB, PE, PI, PR,
    RJ, RN, RO, RR, RS, SC, SE, SP, TO;

    // Método estático que converte a sigla em UF; exige duas letras maiúsculas, sem espaço.
    public static Uf de(String sigla) {
        if (sigla == null) {
            throw new UfInvalida("A sigla de UF não pode ser nula.");
        }
        for (Uf unidadeFederativa : values()) {
            if (unidadeFederativa.name().equals(sigla)) {
                return unidadeFederativa;
            }
        }
        throw new UfInvalida("Sigla de UF desconhecida: \"%s\".".formatted(sigla));
    }

    public String sigla() {
        return name();
    }
}
