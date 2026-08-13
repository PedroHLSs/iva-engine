package br.edu.tcc.auditoria.dominio.catalogo;

import br.edu.tcc.auditoria.dominio.Ncm;
import br.edu.tcc.auditoria.dominio.excecao.RegistroNormativoInvalido;

/**
 * Vínculo entre um NCM e um anexo, numa dada vigência.
 *
 * <p>A chave de vigência é o par NCM e anexo, não o NCM sozinho. A diferença
 * importa: se a chave fosse só o NCM, o catálogo passaria a recusar, como
 * "vigências sobrepostas", um NCM vinculado a dois anexos ao mesmo tempo — o
 * que seria afirmar que isso não pode acontecer. O código não afirma isso;
 * quem afirma é a fonte importada.</p>
 *
 * @param ncm                  NCM vinculado
 * @param identificadorDoAnexo anexo a que o NCM foi vinculado
 * @param tipoDeTratamento     rótulo do tratamento indicado pela fonte
 * @param procedencia          vigência e fonte
 */
public record ItemAnexo(
        Ncm ncm,
        IdentificadorAnexo identificadorDoAnexo,
        String tipoDeTratamento,
        ProcedenciaNormativa procedencia) implements RegistroNormativo {

    private static final String SEPARADOR_DE_CHAVE = "::";

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

    @Override
    public String chaveDeVigencia() {
        return chaveDe(ncm, identificadorDoAnexo);
    }

    /** Chave da série temporal do par NCM e anexo. */
    public static String chaveDe(Ncm ncm, IdentificadorAnexo identificadorDoAnexo) {
        return ncm.valor() + SEPARADOR_DE_CHAVE + identificadorDoAnexo.valor();
    }
}
