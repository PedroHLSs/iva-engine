package br.edu.tcc.auditoria.dominio.catalogo;

import br.edu.tcc.auditoria.dominio.Ncm;
import br.edu.tcc.auditoria.dominio.excecao.RegistroNormativoInvalido;

// Representa o vínculo entre um NCM e um anexo numa vigência; a chave é o par NCM e anexo, porque o catálogo pode vincular o mesmo NCM a mais de um anexo.
public record ItemAnexo(
        Ncm ncm,
        IdentificadorAnexo identificadorDoAnexo,
        String tipoDeTratamento,
        ProcedenciaNormativa procedencia) implements RegistroNormativo {

    private static final String SEPARADOR_DE_CHAVE = "::";

    // Valida que o item tenha NCM, anexo, tipo de tratamento e procedência.
    public ItemAnexo {
        if (ncm == null) {
            throw new RegistroNormativoInvalido("O item de anexo precisa de NCM.");
        }
        if (identificadorDoAnexo == null) {
            throw new RegistroNormativoInvalido(
                    "O item de anexo do NCM \"%s\" precisa identificar o anexo.".formatted(ncm.valor()));
        }
        if (tipoDeTratamento == null || tipoDeTratamento.isBlank()) {
            throw new RegistroNormativoInvalido(
                    "O item de anexo do NCM \"%s\" precisa declarar o tipo de tratamento."
                            .formatted(ncm.valor()));
        }
        if (procedencia == null) {
            throw new RegistroNormativoInvalido(
                    "O item de anexo do NCM \"%s\" precisa de vigência e fonte normativa."
                            .formatted(ncm.valor()));
        }
    }

    // Retorna a chave da série de vigência: o par NCM e anexo.
    @Override
    public String chaveDeVigencia() {
        return chaveDe(ncm, identificadorDoAnexo);
    }

    // Método estático que monta a chave da série do par NCM e anexo.
    public static String chaveDe(Ncm ncm, IdentificadorAnexo identificadorDoAnexo) {
        return ncm.valor() + SEPARADOR_DE_CHAVE + identificadorDoAnexo.valor();
    }
}
