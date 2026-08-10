package br.edu.tcc.auditoria.dominio.excecao;

/**
 * Sinaliza tentativa de guardar, como identificador de participante, um valor
 * que não é um pseudônimo.
 *
 * <p>É esta exceção que impede que um CNPJ, um CPF, uma razão social ou um
 * endereço entrem no domínio por engano.</p>
 */
public class IdentificadorPseudonimizadoInvalido extends ExcecaoDeDominio {

    public IdentificadorPseudonimizadoInvalido(String mensagem) {
        super(mensagem);
    }
}
