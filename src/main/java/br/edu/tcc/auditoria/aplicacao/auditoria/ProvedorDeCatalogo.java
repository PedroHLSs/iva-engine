package br.edu.tcc.auditoria.aplicacao.auditoria;

//Fornece o catálogo de regras e normas para auditoria
public interface ProvedorDeCatalogo {

    CatalogoParaAuditoria carregar();
}
