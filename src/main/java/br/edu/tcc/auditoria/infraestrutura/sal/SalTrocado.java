package br.edu.tcc.auditoria.infraestrutura.sal;

/**
 * O sal resolvido não é o mesmo que produziu os pseudônimos já gravados.
 *
 * <p>Também sinaliza impressão digital malformada, que é a mesma pergunta vista
 * de outro ângulo: um valor que não é impressão digital não pode ser comparado
 * com o sal em uso, e prosseguir sem comparar é o que esta classe existe para
 * impedir.</p>
 */
public class SalTrocado extends RuntimeException {

    public SalTrocado(String mensagem) {
        super(mensagem);
    }
}
