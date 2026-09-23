package br.edu.tcc.auditoria.aplicacao.auditoria;

import java.util.Optional;

//Fornece o catálogo de regras e normas para auditoria por versão específica
public interface ProvedorDeCatalogoPorVersao {

    Optional<CatalogoParaAuditoria> daVersao(String versao);
}
