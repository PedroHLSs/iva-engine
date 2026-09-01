package br.edu.tcc.auditoria.aplicacao.consulta;

import br.edu.tcc.auditoria.dominio.ChaveAcesso;

import java.util.Collection;
import java.util.Map;

/** Porta de leitura dos dados de identificação dos documentos auditados. */
public interface ConsultaDeDocumentos {

    /**
     * Dados dos documentos indicados, indexados pela chave.
     *
     * <p>Consulta em bloco de propósito: o papel de trabalho precisa dos dados de
     * centenas de documentos de uma vez, e uma consulta por linha transformaria a
     * exportação num problema de banco.</p>
     *
     * <p>Chave sem documento gravado simplesmente não aparece no resultado.</p>
     */
    Map<ChaveAcesso, DadosDoDocumento> porChaves(Collection<ChaveAcesso> chaves);
}
