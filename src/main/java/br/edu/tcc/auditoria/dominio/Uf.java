package br.edu.tcc.auditoria.dominio;

import br.edu.tcc.auditoria.dominio.excecao.UfInvalida;

/**
 * Unidade federativa, identificada pela sigla de duas letras.
 *
 * <p>Conjunto fechado: as 26 unidades estaduais e o Distrito Federal. Isto é
 * divisão político-administrativa do país, não conteúdo tributário — nenhuma
 * alíquota, código ou vigência está associada a estas constantes.</p>
 *
 * <p>Documento com destinatário no exterior não recebe uma sigla especial: o
 * campo correspondente em {@link Documento} é {@code Optional} e fica vazio.</p>
 */
public enum Uf {

    AC, AL, AM, AP, BA, CE, DF, ES, GO,
    MA, MG, MS, MT, PA, PB, PE, PI, PR,
    RJ, RN, RO, RR, RS, SC, SE, SP, TO;

    /**
     * Converte uma sigla em unidade federativa.
     *
     * <p>Exige a sigla exatamente como declarada: duas letras maiúsculas. Não
     * apara espaços nem aceita minúsculas — normalizar o texto lido do XML é
     * responsabilidade da infraestrutura.</p>
     *
     * @throws UfInvalida se a sigla for nula ou não corresponder a nenhuma UF
     */
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

    /** Sigla de duas letras maiúsculas. */
    public String sigla() {
        return name();
    }
}
