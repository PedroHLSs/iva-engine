package br.edu.tcc.auditoria.aplicacao.auditoria;

import br.edu.tcc.auditoria.dominio.Documento;
import br.edu.tcc.auditoria.dominio.catalogo.ContextoNormativo;

// Prove o contexto normativo para auditoria de um documento específico, considerando a data de emissão do mesmo.
public interface ProvedorDeContextoNormativo {

    ContextoNormativo contextoPara(Documento documento);
}
