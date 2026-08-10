package br.edu.tcc.auditoria.dominio.excecao;

/**
 * Sinaliza tentativa de construir um item de documento em estado inválido.
 *
 * <p>Inclui o caso central deste projeto: passar {@code null} onde o modelo
 * espera um {@code Optional}. Ausência de informação se representa com
 * {@code Optional.empty()}, nunca com {@code null} nem com zero.</p>
 */
public class ItemDocumentoInvalido extends ExcecaoDeDominio {

    public ItemDocumentoInvalido(String mensagem) {
        super(mensagem);
    }
}
