package br.edu.tcc.auditoria.aplicacao.consulta;

import br.edu.tcc.auditoria.dominio.ChaveAcesso;

import java.util.Collection;
import java.util.Map;

// Interface responsável por consultar os dados de identificação dos documentos auditados.
public interface ConsultaDeDocumentos {

    // Retorna os dados dos documentos indicados, indexados pela chave, numa consulta em bloco; chave sem documento gravado não aparece.
    Map<ChaveAcesso, DadosDoDocumento> porChaves(Collection<ChaveAcesso> chaves);
}
