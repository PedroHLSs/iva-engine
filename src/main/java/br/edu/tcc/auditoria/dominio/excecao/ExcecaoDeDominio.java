package br.edu.tcc.auditoria.dominio.excecao;

/**
 * Raiz de toda exceção lançada pelo domínio.
 *
 * <p>O domínio nunca usa {@code IllegalArgumentException},
 * {@code NullPointerException} ou qualquer exceção genérica da biblioteca
 * padrão para sinalizar dado inválido. Cada tipo de invalidez tem sua própria
 * subclasse, para que quem monta o relatório consiga distinguir uma chave de
 * acesso malformada de um NCM malformado sem inspecionar texto de mensagem.</p>
 */
public abstract class ExcecaoDeDominio extends RuntimeException {

    protected ExcecaoDeDominio(String mensagem) {
        super(mensagem);
    }
}
