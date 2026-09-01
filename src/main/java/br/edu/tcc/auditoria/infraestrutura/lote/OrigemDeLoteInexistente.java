package br.edu.tcc.auditoria.infraestrutura.lote;

/**
 * A origem apontada para auditoria não existe.
 *
 * <p>Tem exceção própria porque é o erro mais comum de uso da linha de comando —
 * caminho digitado errado — e merece uma mensagem que diga isso, e não um rastro
 * de pilha de leitura de arquivo.</p>
 */
public class OrigemDeLoteInexistente extends RuntimeException {

    public OrigemDeLoteInexistente(String mensagem) {
        super(mensagem);
    }
}
