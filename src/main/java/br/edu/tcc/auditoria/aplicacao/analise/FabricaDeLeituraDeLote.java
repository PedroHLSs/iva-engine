package br.edu.tcc.auditoria.aplicacao.analise;

/**
 * Monta uma {@link LeituraDeLote} nova para cada análise.
 *
 * <p>Existe como porta, e não como construção direta, porque montar a cadeia de
 * leitura é assunto da infraestrutura: envolve o leitor de XML, o normalizador e
 * o registro de falhas, nenhum dos quais esta camada conhece.</p>
 */
@FunctionalInterface
public interface FabricaDeLeituraDeLote {

    LeituraDeLote nova();
}
