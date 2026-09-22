package br.edu.tcc.auditoria.aplicacao.analise;

import br.edu.tcc.auditoria.aplicacao.auditoria.FonteDeLoteDeDocumentos;
import br.edu.tcc.auditoria.dominio.tratativa.HashDoItem;

import java.util.List;

/**
 * Uma leitura de lote isolada, com as falhas dela e de mais ninguém.
 *
 * <h2>Por que "isolada" é a palavra que importa</h2>
 *
 * <p>O registro de falhas de leitura da Etapa 4 é um objeto só, vivo enquanto o
 * processo vive. Isso basta para a CLI, onde o processo roda um comando e
 * encerra: as falhas acumuladas são exatamente as daquele lote.</p>
 *
 * <p>Deixa de bastar num servidor, que atende análises em sequência sem
 * reiniciar. Compartilhar o registro faria a segunda análise herdar os arquivos
 * ilegíveis da primeira — e, pior, faria um lote impecável aparecer com falhas
 * que não são dele. Cada análise recebe o seu.</p>
 */
public interface LeituraDeLote {

    /** A fonte a entregar ao serviço de auditoria. */
    FonteDeLoteDeDocumentos fonte();

    /**
     * Os arquivos que falharam nesta leitura, na ordem em que falharam.
     *
     * <p>Só faz sentido depois de a fonte ter sido usada; antes disso é vazia,
     * que é a resposta correta para "nada falhou ainda".</p>
     */
    List<ArquivoIlegivel> arquivosIlegiveis();

    /**
     * A descrição que esta leitura viu para aquele item.
     *
     * <p>Endereçada pelo {@link HashDoItem}, que é a identidade que o sistema já
     * usa para o item em toda parte. Quem grava e quem pergunta calculam o
     * endereço com a mesma função, sobre as mesmas entradas: não há como a
     * descrição se ligar ao produto errado.</p>
     *
     * <p>Nunca devolve nulo. Item que esta leitura não viu volta como ausência
     * declarada, dizendo que a falta é do sistema e não do documento.</p>
     */
    DescricaoDoProduto descricaoDe(HashDoItem hashDoItem);
}
