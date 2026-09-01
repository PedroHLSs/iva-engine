package br.edu.tcc.auditoria.dominio.excecao;

/**
 * Rótulo de gabarito que o sistema não reconhece.
 *
 * <p>O gabarito é escrito à mão por quem conhece os documentos, e o erro de
 * digitação é o defeito mais provável dele. A mensagem lista os rótulos aceitos
 * em vez de dizer apenas que o valor é inválido.</p>
 */
public class RotuloEsperadoInvalido extends ExcecaoDeDominio {

    public RotuloEsperadoInvalido(String mensagem) {
        super(mensagem);
    }
}
