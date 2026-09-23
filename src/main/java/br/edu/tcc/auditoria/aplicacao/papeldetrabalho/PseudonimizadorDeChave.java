package br.edu.tcc.auditoria.aplicacao.papeldetrabalho;

import br.edu.tcc.auditoria.dominio.ChaveAcesso;
import br.edu.tcc.auditoria.dominio.IdentificadorPseudonimizado;

// Interface responsável por trocar a chave de acesso pelo pseudônimo dela, porque os dígitos da chave carregam o CNPJ do emitente.
public interface PseudonimizadorDeChave {

    // Retorna o pseudônimo da chave de acesso indicada, estável dentro da mesma instalação.
    IdentificadorPseudonimizado de(ChaveAcesso chaveAcesso);
}
