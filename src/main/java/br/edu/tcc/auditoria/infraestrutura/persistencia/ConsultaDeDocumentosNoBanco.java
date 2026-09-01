package br.edu.tcc.auditoria.infraestrutura.persistencia;

import br.edu.tcc.auditoria.aplicacao.consulta.ConsultaDeDocumentos;
import br.edu.tcc.auditoria.aplicacao.consulta.DadosDoDocumento;
import br.edu.tcc.auditoria.dominio.ChaveAcesso;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Lê os dados de identificação dos documentos auditados.
 *
 * <p>Devolve apenas o que identifica o documento — modelo, série, número, data e
 * UF do emitente. As colunas de pseudônimo de participante existem na tabela e
 * <strong>não</strong> passam por aqui: o papel de trabalho não precisa delas, e
 * dado que não é carregado não é dado que vaza.</p>
 */
@Component
class ConsultaDeDocumentosNoBanco implements ConsultaDeDocumentos {

    private final DocumentoJpa documentos;

    ConsultaDeDocumentosNoBanco(DocumentoJpa documentos) {
        this.documentos = documentos;
    }

    @Override
    @Transactional(readOnly = true)
    public Map<ChaveAcesso, DadosDoDocumento> porChaves(Collection<ChaveAcesso> chaves) {
        if (chaves == null || chaves.isEmpty()) {
            return Map.of();
        }
        Set<String> valores = new LinkedHashSet<>();
        chaves.forEach(chave -> valores.add(chave.valor()));

        Map<ChaveAcesso, DadosDoDocumento> encontrados = new LinkedHashMap<>();
        for (DocumentoEntidade entidade : documentos.findAllById(valores)) {
            ChaveAcesso chave = new ChaveAcesso(entidade.chaveAcesso());
            encontrados.put(chave, new DadosDoDocumento(
                    chave,
                    entidade.modelo(),
                    entidade.serie(),
                    entidade.numero(),
                    entidade.dataEmissao(),
                    entidade.ufEmitente()));
        }
        return Map.copyOf(encontrados);
    }
}
