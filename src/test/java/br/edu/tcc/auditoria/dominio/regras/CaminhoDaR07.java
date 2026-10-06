package br.edu.tcc.auditoria.dominio.regras;

import br.edu.tcc.auditoria.dominio.CodigoClassificacaoTributaria;
import br.edu.tcc.auditoria.dominio.CodigoCst;
import br.edu.tcc.auditoria.dominio.ItemDocumento;
import br.edu.tcc.auditoria.dominio.ResultadoAvaliacao;
import br.edu.tcc.auditoria.dominio.catalogo.CatalogoFicticio;
import br.edu.tcc.auditoria.dominio.catalogo.ClassificacaoTributaria;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;

// Enum com todos os caminhos da R07 (D015, 03/10/2026): para cada um, diz se o catálogo afirmou algo sobre os campos exigidos daquele código e qual resultado a regra deve dar. Dados inteiramente fictícios.
public enum CaminhoDaR07 {

    ITEM_SEM_CCLASSTRIB(false, ResultadoAvaliacao.NAO_AVALIADO,
            () -> ConstrutorDeItem.item().construir(),
            () -> exigindo(Optional.of(List.of(CampoDoItem.BASE_CALCULO_IBS.nomeNoCatalogo())))),

    CODIGO_FORA_DO_CATALOGO(false, ResultadoAvaliacao.NAO_AVALIADO,
            () -> ConstrutorDeItem.item().classificacao(CenarioFicticio.CODIGO).construir(),
            ContextoNormativoFalso::vazio),

    // O caso do defeito: célula em branco no CSV. Até a R07 1.0.0, CONFORME.
    CAMPOS_NAO_DECLARADOS(false, ResultadoAvaliacao.NAO_AVALIADO,
            () -> ConstrutorDeItem.item().classificacao(CenarioFicticio.CODIGO).construir(),
            () -> exigindo(Optional.empty())),

    NENHUM_CAMPO_DECLARADO(true, ResultadoAvaliacao.CONFORME,
            () -> ConstrutorDeItem.item().classificacao(CenarioFicticio.CODIGO).construir(),
            () -> exigindo(Optional.of(List.of()))),

    CAMPO_DECLARADO_E_PREENCHIDO(true, ResultadoAvaliacao.CONFORME,
            () -> ConstrutorDeItem.item().classificacao(CenarioFicticio.CODIGO).baseCalculoIbs("99.99").construir(),
            () -> exigindo(Optional.of(List.of(CampoDoItem.BASE_CALCULO_IBS.nomeNoCatalogo())))),

    CAMPO_DECLARADO_E_AUSENTE(true, ResultadoAvaliacao.ACHADO,
            () -> ConstrutorDeItem.item().classificacao(CenarioFicticio.CODIGO).construir(),
            () -> exigindo(Optional.of(List.of(CampoDoItem.BASE_CALCULO_IBS.nomeNoCatalogo())))),

    SO_NOME_NAO_RECONHECIDO(true, ResultadoAvaliacao.NAO_AVALIADO,
            () -> ConstrutorDeItem.item().classificacao(CenarioFicticio.CODIGO).construir(),
            () -> exigindo(Optional.of(List.of("campoInexistenteXX")))),

    NOME_NAO_RECONHECIDO_E_CAMPO_PREENCHIDO(true, ResultadoAvaliacao.NAO_AVALIADO,
            () -> ConstrutorDeItem.item().classificacao(CenarioFicticio.CODIGO).baseCalculoIbs("99.99").construir(),
            () -> exigindo(Optional.of(List.of(CampoDoItem.BASE_CALCULO_IBS.nomeNoCatalogo(), "campoInexistenteXX")))),

    NOME_NAO_RECONHECIDO_E_CAMPO_AUSENTE(true, ResultadoAvaliacao.ACHADO,
            () -> ConstrutorDeItem.item().classificacao(CenarioFicticio.CODIGO).construir(),
            () -> exigindo(Optional.of(List.of(CampoDoItem.BASE_CALCULO_IBS.nomeNoCatalogo(), "campoInexistenteXX"))));

    private final boolean catalogoAfirmou;
    private final ResultadoAvaliacao esperado;
    private final Supplier<ItemDocumento> item;
    private final Supplier<ContextoNormativoFalso> contexto;

    // Construtor que guarda o que o caminho afirma, o resultado esperado e como montar item e catálogo.
    CaminhoDaR07(boolean catalogoAfirmou, ResultadoAvaliacao esperado,
            Supplier<ItemDocumento> item, Supplier<ContextoNormativoFalso> contexto) {
        this.catalogoAfirmou = catalogoAfirmou;
        this.esperado = esperado;
        this.item = item;
        this.contexto = contexto;
    }

    // Diz se o catálogo afirmou algo sobre os campos exigidos do código do item: uma lista de nomes, ou NENHUM.
    public boolean catalogoAfirmou() {
        return catalogoAfirmou;
    }

    public ResultadoAvaliacao esperado() {
        return esperado;
    }

    // Roda a R07 sobre o item e o catálogo deste caminho.
    public Avaliacao avaliar() {
        return new RegraCamposObrigatoriosPreenchidos()
                .avaliar(item.get(), CenarioFicticio.documento(), contexto.get());
    }

    // Método auxiliar que monta o catálogo fictício com a declaração de campos indicada, pelo construtor canônico.
    private static ContextoNormativoFalso exigindo(Optional<List<String>> campos) {
        return ContextoNormativoFalso.vazio().com(new ClassificacaoTributaria(
                new CodigoClassificacaoTributaria(CenarioFicticio.CODIGO),
                Set.of(new CodigoCst(CenarioFicticio.CST)),
                CatalogoFicticio.DISPOSITIVO,
                false,
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                campos,
                CenarioFicticio.procedencia()));
    }
}
