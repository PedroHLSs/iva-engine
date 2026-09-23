package br.edu.tcc.auditoria.aplicacao.conferencia;

import br.edu.tcc.auditoria.aplicacao.auditoria.ProvedorDeCatalogoPorVersao;
import br.edu.tcc.auditoria.aplicacao.catalogo.RepositorioDeCargaDeCatalogo;
import br.edu.tcc.auditoria.dominio.CodigoClassificacaoTributaria;
import br.edu.tcc.auditoria.dominio.Ncm;

import java.time.LocalDate;
import java.util.Optional;

// Serviço que consulta a base tributária mais recente na data informada, sem data padrão.
public final class ConsultaDaBaseTributaria {

    private final RepositorioDeCargaDeCatalogo cargas;
    private final ProvedorDeCatalogoPorVersao catalogos;

    // Construtor da consulta, que recebe o repositório de cargas e o provedor de catálogo por versão.
    public ConsultaDaBaseTributaria(
            RepositorioDeCargaDeCatalogo cargas, ProvedorDeCatalogoPorVersao catalogos) {

        if (cargas == null || catalogos == null) {
            throw new ConferenciaInvalida(
                    "A consulta à base tributária precisa do repositório de cargas e do provedor de "
                            + "catálogo por versão.");
        }
        this.cargas = cargas;
        this.catalogos = catalogos;
    }

    // Retorna a base na data indicada, com as consultas por NCM e por cClassTrib que tiverem sido pedidas.
    public BaseTributariaEm em(
            LocalDate data, Optional<Ncm> ncm, Optional<CodigoClassificacaoTributaria> codigo) {

        if (data == null) {
            throw new ConferenciaInvalida(
                    "A consulta à base tributária exige data. Sem ela a resposta seria a de alguma data "
                            + "escolhida pelo sistema, e ninguém saberia qual.");
        }
        if (ncm == null || codigo == null) {
            throw new ConferenciaInvalida(
                    "Não perguntar por NCM ou por cClassTrib se representa com Optional.empty().");
        }

        BaseNormativa base = cargas.versaoDaCargaMaisRecente()
                .map(versao -> BaseNormativa.daVersao(catalogos, versao))
                .orElseGet(BaseNormativa::nenhumaCargaImportada);

        return new BaseTributariaEm(
                data,
                base.versaoDoCatalogo(),
                base.cargaDisponivel(),
                base.natureza(),
                base.cobertura(),
                base.aliquotasEm(data),
                ncm.map(consultado -> base.ncmEm(data, consultado)),
                ncm.map(consultado -> base.anexosEm(data, consultado)),
                codigo.map(consultado -> base.classificacaoEm(data, consultado)));
    }
}
