package br.edu.tcc.auditoria.aplicacao.catalogo;

import br.edu.tcc.auditoria.dominio.excecao.CatalogoInvalido;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * A natureza de cada tabela da carga, e o que ela diz sobre a carga inteira.
 *
 * <h2>Por tabela, e não por carga</h2>
 *
 * <p>Um único sinalizador não consegue descrever o caso que interessa: alguém
 * carregar um anexo real e o resto fictício. Esse é o cenário que a bagunça de
 * {@code exemplos/} quase produziu, e é justamente onde uma marcação binária
 * falha — ela teria de escolher entre chamar a carga de real ou de fictícia, e as
 * duas respostas estariam erradas.</p>
 *
 * <p>Guardada por tabela, a derivação consegue dizer "parcialmente fictício" e
 * <strong>listar quais tabelas</strong>. Quem lê sabe em que parte da tela pode
 * confiar.</p>
 *
 * <h2>Tabela vazia não tem natureza, e isso não é omissão</h2>
 *
 * <p>A natureza é declarada linha a linha. Uma tabela fornecida só com o
 * cabeçalho — que é como esta carga declara "não trago registro nenhum aqui" —
 * não tem linha onde declará-la. {@link Optional} vazio é essa ausência, e
 * {@link CargaDeCatalogo} confere que ela coincide exatamente com a lista
 * vazia.</p>
 */
public record NaturezaDaCarga(
        Optional<Natureza> classificacoesTributarias,
        Optional<Natureza> registrosDeNcm,
        Optional<Natureza> itensDeAnexo,
        Optional<Natureza> aliquotas) {

    /** Nome de tabela como ele aparece na tela e no banco. */
    public static final String CLASSIFICACOES_TRIBUTARIAS = "CLASSIFICACAO_TRIBUTARIA";
    public static final String REGISTROS_DE_NCM = "NCM";
    public static final String ITENS_DE_ANEXO = "ITEM_ANEXO";
    public static final String ALIQUOTAS = "ALIQUOTA";

    public NaturezaDaCarga {
        exigir(classificacoesTributarias, CLASSIFICACOES_TRIBUTARIAS);
        exigir(registrosDeNcm, REGISTROS_DE_NCM);
        exigir(itensDeAnexo, ITENS_DE_ANEXO);
        exigir(aliquotas, ALIQUOTAS);
    }

    /**
     * A carga foi gravada antes de a natureza passar a ser declarada.
     *
     * <p>Nenhuma tabela tem natureza, e a tela diz isso. <strong>Não se supõe
     * {@code NORMATIVO}</strong>: supor faria o sistema afirmar que dado de
     * procedência desconhecida é norma vigente, que é a afirmação mais cara que
     * ele poderia fazer por engano.</p>
     */
    public static NaturezaDaCarga naoDeclarada() {
        return new NaturezaDaCarga(
                Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty());
    }

    /**
     * A mesma procedência para todas as tabelas que têm registro.
     *
     * <p>Atalho para a carga que veio de uma origem só — o caso comum de um
     * acervo de demonstração inteiro, ou de um catálogo transcrito de uma vez.</p>
     *
     * <p><strong>O caminho de importação não usa este atalho.</strong> Lá cada
     * arquivo declara a sua, e é justamente a possibilidade de elas diferirem que
     * permite representar o caso parcialmente fictício. Usá-lo na importação
     * apagaria a mistura que a marcação existe para mostrar.</p>
     */
    public static NaturezaDaCarga deUmaSoProcedencia(
            Natureza natureza,
            List<?> classificacoesTributarias,
            List<?> registrosDeNcm,
            List<?> itensDeAnexo,
            List<?> aliquotas) {

        if (natureza == null) {
            throw new CatalogoInvalido("Não há procedência a declarar.");
        }
        return new NaturezaDaCarga(
                seHouver(natureza, classificacoesTributarias),
                seHouver(natureza, registrosDeNcm),
                seHouver(natureza, itensDeAnexo),
                seHouver(natureza, aliquotas));
    }

    private static Optional<Natureza> seHouver(Natureza natureza, List<?> registros) {
        if (registros == null) {
            throw new CatalogoInvalido(
                    "A lista deve ser vazia quando a tabela não traz registro, nunca nula.");
        }
        return registros.isEmpty() ? Optional.empty() : Optional.of(natureza);
    }

    /** As naturezas declaradas, por tabela, na ordem em que a carga as traz. */
    public Map<String, Natureza> declaradas() {
        Map<String, Natureza> porTabela = new LinkedHashMap<>();
        classificacoesTributarias.ifPresent(
                natureza -> porTabela.put(CLASSIFICACOES_TRIBUTARIAS, natureza));
        registrosDeNcm.ifPresent(natureza -> porTabela.put(REGISTROS_DE_NCM, natureza));
        itensDeAnexo.ifPresent(natureza -> porTabela.put(ITENS_DE_ANEXO, natureza));
        aliquotas.ifPresent(natureza -> porTabela.put(ALIQUOTAS, natureza));
        // unmodifiableMap sobre LinkedHashMap, e nao Map.copyOf: a copia imutavel
        // do Map nao preserva ordem de insercao, e a faixa listaria as tabelas em
        // ordem diferente a cada abertura da tela.
        return Collections.unmodifiableMap(porTabela);
    }

    /** As tabelas cujo conteúdo é de demonstração. */
    public List<String> tabelasFicticias() {
        List<String> ficticias = new ArrayList<>();
        declaradas().forEach((tabela, natureza) -> {
            if (natureza == Natureza.FICTICIO) {
                ficticias.add(tabela);
            }
        });
        return List.copyOf(ficticias);
    }

    /**
     * O que a carga é, no conjunto.
     *
     * <p>Derivado das tabelas, e não declarado à parte: duas declarações do mesmo
     * fato podem divergir, e a derivada é a que não tem como mentir.</p>
     */
    public SituacaoDaNatureza situacao() {
        Map<String, Natureza> declaradas = declaradas();
        if (declaradas.isEmpty()) {
            return SituacaoDaNatureza.NAO_DECLARADA;
        }
        boolean temFicticio = declaradas.containsValue(Natureza.FICTICIO);
        boolean temNormativo = declaradas.containsValue(Natureza.NORMATIVO);
        if (temFicticio && temNormativo) {
            return SituacaoDaNatureza.PARCIALMENTE_FICTICIO;
        }
        return temFicticio ? SituacaoDaNatureza.INTEIRAMENTE_FICTICIO : SituacaoDaNatureza.NORMATIVO;
    }

    private static void exigir(Optional<Natureza> natureza, String tabela) {
        if (natureza == null) {
            throw new CatalogoInvalido(
                    ("A natureza da tabela %s deve ser Optional.empty() quando a tabela não tem "
                            + "registro, nunca nula.").formatted(tabela));
        }
    }
}
