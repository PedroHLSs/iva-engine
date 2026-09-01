package br.edu.tcc.auditoria.dominio.excecao;

/** Tentativa de registrar tratativa sem decisão, sem justificativa ou sem alvo. */
public class TratativaInvalida extends ExcecaoDeDominio {

    public TratativaInvalida(String mensagem) {
        super(mensagem);
    }
}
