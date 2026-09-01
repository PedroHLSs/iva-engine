package br.edu.tcc.auditoria.infraestrutura.csv;

/**
 * Como quem chamou o leitor quer que um CSV malformado seja recusado.
 *
 * <p>O leitor sabe reconhecer cabeçalho repetido, linha com número errado de
 * campos e data fora de formato, mas não sabe de que assunto é o arquivo. Quem
 * o chama sabe, e é quem tem de nomear a falha: catálogo malformado e gabarito
 * malformado são problemas de pessoas diferentes, resolvidos em arquivos
 * diferentes, e a linha de comando os trata separadamente.</p>
 *
 * <p>Sem isto o leitor teria de lançar um tipo único, e a importação de catálogo
 * passaria a recusar arquivo de gabarito com mensagem de catálogo — ou o
 * contrário, que é pior.</p>
 */
@FunctionalInterface
public interface RecusaDeCsv {

    /** Monta a exceção que recusa o arquivo, com a causa original quando houver. */
    RuntimeException de(String mensagem, Throwable causa);

    /** Monta a exceção que recusa o arquivo, quando não há causa a preservar. */
    default RuntimeException de(String mensagem) {
        return de(mensagem, null);
    }
}
