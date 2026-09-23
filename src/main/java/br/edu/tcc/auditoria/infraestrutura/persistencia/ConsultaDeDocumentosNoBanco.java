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

// Classe que lê do banco os dados que identificam os documentos: modelo, série, número, data e UF do emitente. Os pseudônimos dos participantes não são lidos aqui, porque quem usa esta consulta não precisa deles.
@Component
class ConsultaDeDocumentosNoBanco implements ConsultaDeDocumentos {

    private final DocumentoJpa documentos;

    // Construtor que recebe o repositório de documentos.
    ConsultaDeDocumentosNoBanco(DocumentoJpa documentos) {
        this.documentos = documentos;
    }

    // Busca os dados dos documentos das chaves informadas.
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
