package br.edu.tcc.auditoria.aplicacao.auditoria;

import br.edu.tcc.auditoria.aplicacao.catalogo.NaturezaDaCarga;
import br.edu.tcc.auditoria.dominio.Documento;
import br.edu.tcc.auditoria.dominio.catalogo.ContextoNormativo;
import br.edu.tcc.auditoria.dominio.catalogo.ContextoNormativoNaData;
import br.edu.tcc.auditoria.dominio.catalogo.RepositorioAliquota;
import br.edu.tcc.auditoria.dominio.catalogo.RepositorioClassificacaoTributaria;
import br.edu.tcc.auditoria.dominio.catalogo.RepositorioItemAnexo;
import br.edu.tcc.auditoria.dominio.catalogo.RepositorioNcm;
import br.edu.tcc.auditoria.dominio.regras.CoberturaDoCatalogo;

// Representa um catálogo de dados normativos que pode ser usado para auditoria, incluindo versão, cobertura, natureza da carga e repositórios de dados.
public record CatalogoParaAuditoria(
        String versao,
        CoberturaDoCatalogo cobertura,
        NaturezaDaCarga natureza,
        RepositorioClassificacaoTributaria classificacoesTributarias,
        RepositorioNcm registrosDeNcm,
        RepositorioItemAnexo itensDeAnexo,
        RepositorioAliquota aliquotas) implements ProvedorDeContextoNormativo {

    public CatalogoParaAuditoria {
        if (versao == null || versao.isBlank()) {
            throw new AuditoriaInvalida(
                    "O catálogo carregado precisa de versão: a execução a registra para que o relatório "
                            + "diga contra qual catálogo foi produzido.");
        }
        exigir(cobertura, "a cobertura declarada da carga");
        exigir(natureza, "a procedência declarada da carga");
        exigir(classificacoesTributarias, "o repositório de classificações tributárias");
        exigir(registrosDeNcm, "o repositório de NCM");
        exigir(itensDeAnexo, "o repositório de itens de anexo");
        exigir(aliquotas, "o repositório de alíquotas");
    }

    @Override
    public ContextoNormativo contextoPara(Documento documento) {
        if (documento == null) {
            throw new AuditoriaInvalida("Não há documento para o qual montar contexto normativo.");
        }
        return new ContextoNormativoNaData(
                documento.dataEmissao(),
                classificacoesTributarias,
                registrosDeNcm,
                itensDeAnexo,
                aliquotas);
    }

    private static void exigir(Object valor, String oQueFalta) {
        if (valor == null) {
            throw new AuditoriaInvalida("O catálogo carregado precisa de %s.".formatted(oQueFalta));
        }
    }
}
