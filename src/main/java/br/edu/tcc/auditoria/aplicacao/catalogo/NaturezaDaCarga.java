package br.edu.tcc.auditoria.aplicacao.catalogo;

import br.edu.tcc.auditoria.dominio.excecao.CatalogoInvalido;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

// Representa a natureza declarada de cada tabela da carga e o que ela diz sobre a carga inteira; tabela sem registro fica com Optional vazio. Desde 01/10/2026 inclui a tabela de anexos declarados (ANEXO_DECLARADO), que entra na faixa e na situação como as outras; carga sem ela continua com as quatro de antes.
// Emenda de 04/10/2026 (D021): inclui a cobertura (COBERTURA), cujo cobertura.csv traz a fonte que os apontamentos citam como fundamento e ficava fora da natureza. A cobertura sempre tem conteúdo, então cobertura sem natureza é procedência não declarada — o caso das cargas gravadas antes desta data —, e a carga deixa de poder ser dita normativa.
public record NaturezaDaCarga(
        Optional<Natureza> classificacoesTributarias,
        Optional<Natureza> registrosDeNcm,
        Optional<Natureza> itensDeAnexo,
        Optional<Natureza> aliquotas,
        Optional<Natureza> anexosDeclarados,
        Optional<Natureza> cobertura) {

    // Nomes das tabelas como aparecem na tela e no banco.
    public static final String CLASSIFICACOES_TRIBUTARIAS = "CLASSIFICACAO_TRIBUTARIA";
    public static final String REGISTROS_DE_NCM = "NCM";
    public static final String ITENS_DE_ANEXO = "ITEM_ANEXO";
    public static final String ALIQUOTAS = "ALIQUOTA";
    public static final String ANEXOS_DECLARADOS = "ANEXO_DECLARADO";
    public static final String COBERTURA = "COBERTURA";

    // Construtor com a aridade anterior a 01/10/2026: a tabela de anexos declarados e a cobertura ficam sem natureza.
    public NaturezaDaCarga(
            Optional<Natureza> classificacoesTributarias,
            Optional<Natureza> registrosDeNcm,
            Optional<Natureza> itensDeAnexo,
            Optional<Natureza> aliquotas) {
        this(classificacoesTributarias, registrosDeNcm, itensDeAnexo, aliquotas, Optional.empty(), Optional.empty());
    }

    // Construtor com a aridade anterior a 04/10/2026: a cobertura fica sem natureza, o que é procedência não declarada.
    public NaturezaDaCarga(
            Optional<Natureza> classificacoesTributarias,
            Optional<Natureza> registrosDeNcm,
            Optional<Natureza> itensDeAnexo,
            Optional<Natureza> aliquotas,
            Optional<Natureza> anexosDeclarados) {
        this(classificacoesTributarias, registrosDeNcm, itensDeAnexo, aliquotas, anexosDeclarados, Optional.empty());
    }

    // Valida que nenhuma tabela venha com natureza nula.
    public NaturezaDaCarga {
        exigir(classificacoesTributarias, CLASSIFICACOES_TRIBUTARIAS);
        exigir(registrosDeNcm, REGISTROS_DE_NCM);
        exigir(itensDeAnexo, ITENS_DE_ANEXO);
        exigir(aliquotas, ALIQUOTAS);
        exigir(anexosDeclarados, ANEXOS_DECLARADOS);
        exigir(cobertura, COBERTURA);
    }

    // Retorna a natureza de uma carga gravada antes de a declaração existir, sem supor que ela seja normativa.
    public static NaturezaDaCarga naoDeclarada() {
        return new NaturezaDaCarga(
                Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty());
    }

    // Atalho que aplica a mesma natureza a todas as tabelas com registro; a importação não o usa, porque lá cada arquivo declara a sua. Desde 04/10/2026 (D021) aplica também à cobertura, que sempre tem conteúdo.
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
                seHouver(natureza, aliquotas),
                Optional.empty(),
                Optional.of(natureza));
    }

    // Método auxiliar que devolve a natureza apenas quando a tabela tem registros.
    private static Optional<Natureza> seHouver(Natureza natureza, List<?> registros) {
        if (registros == null) {
            throw new CatalogoInvalido(
                    "A lista deve ser vazia quando a tabela não traz registro, nunca nula.");
        }
        return registros.isEmpty() ? Optional.empty() : Optional.of(natureza);
    }

    // Retorna as naturezas declaradas por tabela, na ordem em que a carga as traz.
    public Map<String, Natureza> declaradas() {
        Map<String, Natureza> porTabela = new LinkedHashMap<>();
        classificacoesTributarias.ifPresent(
                natureza -> porTabela.put(CLASSIFICACOES_TRIBUTARIAS, natureza));
        registrosDeNcm.ifPresent(natureza -> porTabela.put(REGISTROS_DE_NCM, natureza));
        itensDeAnexo.ifPresent(natureza -> porTabela.put(ITENS_DE_ANEXO, natureza));
        aliquotas.ifPresent(natureza -> porTabela.put(ALIQUOTAS, natureza));
        anexosDeclarados.ifPresent(natureza -> porTabela.put(ANEXOS_DECLARADOS, natureza));
        cobertura.ifPresent(natureza -> porTabela.put(COBERTURA, natureza));
        // Usa unmodifiableMap, e não Map.copyOf, para preservar a ordem das tabelas na faixa da tela.
        return Collections.unmodifiableMap(porTabela);
    }

    // Retorna a lista das tabelas cujo conteúdo é de demonstração.
    public List<String> tabelasFicticias() {
        List<String> ficticias = new ArrayList<>();
        declaradas().forEach((tabela, natureza) -> {
            if (natureza == Natureza.FICTICIO) {
                ficticias.add(tabela);
            }
        });
        return List.copyOf(ficticias);
    }

    // D021: retorna as tabelas que têm conteúdo e não declararam natureza. Só a cobertura se sabe ter conteúdo sem olhar os registros, então é a única que entra aqui.
    public List<String> tabelasSemNaturezaDeclarada() {
        return cobertura.isPresent() ? List.of() : List.of(COBERTURA);
    }

    // Retorna a situação da carga inteira, derivada das naturezas de cada tabela em vez de declarada à parte.
    // Emenda de 04/10/2026 (D021): tabela com conteúdo e sem natureza — a cobertura das cargas anteriores a esta data — impede a situação normativa. Havendo tabela fictícia, a situação continua sendo a fictícia; não havendo, é procedência não declarada.
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
        if (temFicticio) {
            return SituacaoDaNatureza.INTEIRAMENTE_FICTICIO;
        }
        return tabelasSemNaturezaDeclarada().isEmpty()
                ? SituacaoDaNatureza.NORMATIVO
                : SituacaoDaNatureza.NAO_DECLARADA;
    }

    // Método auxiliar para verificar se a natureza da tabela é nula e lançar uma exceção com uma mensagem apropriada.
    private static void exigir(Optional<Natureza> natureza, String tabela) {
        if (natureza == null) {
            throw new CatalogoInvalido(
                    ("A natureza da tabela %s deve ser Optional.empty() quando a tabela não tem "
                            + "registro, nunca nula.").formatted(tabela));
        }
    }
}
