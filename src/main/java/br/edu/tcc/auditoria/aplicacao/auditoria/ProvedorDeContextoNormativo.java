package br.edu.tcc.auditoria.aplicacao.auditoria;

import br.edu.tcc.auditoria.dominio.Documento;
import br.edu.tcc.auditoria.dominio.catalogo.ContextoNormativo;

/**
 * Constrói o contexto normativo de cada documento auditado.
 *
 * <p>Existe por causa da decisão D003: o catálogo é resolvido na data de emissão
 * do documento, e um lote de documentos com datas diferentes precisa de um
 * contexto por documento. Um contexto único compartilhado por todo o lote
 * auditaria documentos antigos contra norma que ainda não valia quando foram
 * emitidos — erro silencioso, com relatório de aparência impecável.</p>
 *
 * <p>É a aplicação que declara esta porta e a infraestrutura que a implementa,
 * ligando os repositórios do catálogo. Enquanto não houver banco, uma
 * implementação de teste ou um lambda sobre repositórios em memória basta.</p>
 */
public interface ProvedorDeContextoNormativo {

    /** O catálogo resolvido na data de emissão deste documento. */
    ContextoNormativo contextoPara(Documento documento);
}
