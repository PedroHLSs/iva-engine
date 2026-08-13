package br.edu.tcc.auditoria.dominio.excecao;

/**
 * Sinaliza registro do catálogo normativo construído sem os dados que o tornam
 * utilizável.
 *
 * <p>Inclui o caso mais importante: registro sem vigência ou sem fonte
 * normativa. Um registro assim não pode ser resolvido no tempo nem citado num
 * apontamento, então não pode existir.</p>
 */
public class RegistroNormativoInvalido extends ExcecaoDeDominio {

    public RegistroNormativoInvalido(String mensagem) {
        super(mensagem);
    }
}
