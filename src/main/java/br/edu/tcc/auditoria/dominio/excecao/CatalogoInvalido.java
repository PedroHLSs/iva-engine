package br.edu.tcc.auditoria.dominio.excecao;

/**
 * Sinaliza catálogo normativo internamente contraditório.
 *
 * <p>O caso central é a sobreposição de vigências: duas versões do mesmo
 * registro valendo na mesma data. Isso não é dado ruim de documento fiscal, é
 * erro de catálogo — quem consultar não tem como saber qual das duas responder,
 * e responder qualquer uma seria arbitrário. Por isso a carga falha, em vez de
 * escolher.</p>
 */
public class CatalogoInvalido extends ExcecaoDeDominio {

    public CatalogoInvalido(String mensagem) {
        super(mensagem);
    }
}
