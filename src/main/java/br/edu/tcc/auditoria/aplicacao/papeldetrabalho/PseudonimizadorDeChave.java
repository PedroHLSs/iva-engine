package br.edu.tcc.auditoria.aplicacao.papeldetrabalho;

import br.edu.tcc.auditoria.dominio.ChaveAcesso;
import br.edu.tcc.auditoria.dominio.IdentificadorPseudonimizado;

/**
 * Porta que troca a chave de acesso pelo pseudônimo dela.
 *
 * <p>Existe porque a chave de acesso não é um identificador neutro: os dígitos
 * dela carregam o CNPJ do emitente. Exportar a chave seria exportar o CNPJ com
 * uma etapa a mais de trabalho para lê-lo.</p>
 *
 * <p>O pseudônimo é estável dentro de uma instalação — a mesma nota sempre gera o
 * mesmo texto —, de modo que duas planilhas do mesmo acervo podem ser cruzadas
 * entre si. Entre instalações com sais diferentes, não.</p>
 */
public interface PseudonimizadorDeChave {

    /** Pseudônimo da chave de acesso indicada. */
    IdentificadorPseudonimizado de(ChaveAcesso chaveAcesso);
}
