package br.edu.tcc.auditoria.aplicacao.auditoria;

//Repositorio utilizado para gravar o resultado da auditoria
public interface RepositorioDaAuditoria {

    void persistir(ResultadoDaAuditoria resultado);
}
