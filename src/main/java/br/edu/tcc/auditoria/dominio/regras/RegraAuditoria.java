package br.edu.tcc.auditoria.dominio.regras;

import br.edu.tcc.auditoria.dominio.Documento;
import br.edu.tcc.auditoria.dominio.ItemDocumento;
import br.edu.tcc.auditoria.dominio.Severidade;
import br.edu.tcc.auditoria.dominio.catalogo.ContextoNormativo;

// Modelo que toda regra segue. A regra não guarda estado. Se faltar dado para decidir, a resposta é NAO_AVALIADO com o motivo, nunca CONFORME. Nenhum valor da lei fica escrito no código.
public interface RegraAuditoria {

    // Devolve o código da regra (R01, R02...), que aparece no relatório e no achado.
    String id();

    // Devolve a versão da regra. Se a lógica mudar, muda a versão da regra e a do conjunto.
    String versao();

    // Devolve a gravidade dos achados desta regra.
    Severidade severidade();

    // Aplica a regra a um item, usando o catálogo já filtrado pela data de emissão da nota.
    Avaliacao avaliar(ItemDocumento item, Documento documento, ContextoNormativo contexto);
}
