package br.edu.tcc.auditoria.aplicacao.catalogo;

import br.edu.tcc.auditoria.dominio.catalogo.AliquotaVigente;
import br.edu.tcc.auditoria.dominio.catalogo.ClassificacaoTributaria;
import br.edu.tcc.auditoria.dominio.catalogo.ItemAnexo;
import br.edu.tcc.auditoria.dominio.catalogo.RegistroNcm;
import br.edu.tcc.auditoria.dominio.catalogo.RegistroNormativo;
import br.edu.tcc.auditoria.dominio.catalogo.SerieNormativa;
import br.edu.tcc.auditoria.dominio.excecao.CatalogoInvalido;
import br.edu.tcc.auditoria.dominio.regras.CoberturaDoCatalogo;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Um catálogo normativo inteiro, pronto para ser gravado, com a cobertura que
 * quem o importou declarou.
 *
 * <p>Todo conteúdo aqui vem de arquivo fornecido pelo usuário em tempo de
 * execução. Nada disto está escrito no código do sistema, e nada disto entra por
 * migration — as tabelas nascem vazias.</p>
 *
 * <h2>A cobertura é declarada, não deduzida</h2>
 *
 * <p>{@link CoberturaDoCatalogo} diz, por tabela, qual período e qual fonte
 * normativa a carga cobre. É o que permite às regras separar duas coisas muito
 * diferentes: "o catálogo foi carregado para esta data e não traz este registro"
 * — que é apontamento — de "esta tabela não foi carregada para esta data" — que
 * é não avaliado. O sistema não infere cobertura a partir das linhas
 * importadas: uma carga incompleta pareceria completa.</p>
 *
 * <h2>A procedência é declarada por tabela</h2>
 *
 * <p>{@link NaturezaDaCarga} diz, por tabela, se o conteúdo é transcrição de
 * fonte normativa ou dado de demonstração. É fato sobre o arquivo importado, e
 * não configuração de quem roda: uma propriedade de instalação seria promessa de
 * quem configurou, e quem esquecesse de ligá-la veria dado fictício apresentado
 * como norma vigente.</p>
 *
 * <p>O construtor casa natureza e conteúdo tabela a tabela: tabela com registro
 * exige natureza declarada, e tabela vazia — o arquivo fornecido só com o
 * cabeçalho — não tem linha em que declará-la.</p>
 *
 * <h2>Vigências sobrepostas param a importação</h2>
 *
 * <p>O construtor agrupa cada tabela por chave de vigência e monta a
 * {@link SerieNormativa} correspondente, que recusa duas versões do mesmo
 * registro valendo na mesma data. A recusa acontece antes de qualquer gravação:
 * um catálogo ambíguo não chega ao banco. Ver D003.</p>
 */
public record CargaDeCatalogo(
        String versao,
        CoberturaDoCatalogo cobertura,
        NaturezaDaCarga natureza,
        List<ClassificacaoTributaria> classificacoesTributarias,
        List<RegistroNcm> registrosDeNcm,
        List<ItemAnexo> itensDeAnexo,
        List<AliquotaVigente> aliquotas) {

    public CargaDeCatalogo {
        if (versao == null || versao.isBlank()) {
            throw new CatalogoInvalido(
                    "A carga de catálogo precisa de versão: é o que a execução de auditoria registra "
                            + "para dizer contra qual catálogo o relatório foi produzido.");
        }
        if (cobertura == null) {
            throw new CatalogoInvalido(
                    "A carga precisa declarar sua cobertura por tabela. Sem ela, silêncio do catálogo "
                            + "não se distingue de tabela não carregada.");
        }

        if (natureza == null) {
            throw new CatalogoInvalido(
                    "A carga precisa declarar a natureza de cada tabela. Sem ela, a tela não tem como "
                            + "avisar que está exibindo dado de demonstração, e dado de demonstração "
                            + "sem aviso é afirmação falsa sobre a lei.");
        }

        classificacoesTributarias = validar(classificacoesTributarias, "classificações tributárias");
        registrosDeNcm = validar(registrosDeNcm, "NCM");
        itensDeAnexo = validar(itensDeAnexo, "itens de anexo");
        aliquotas = validar(aliquotas, "alíquotas");

        casar(natureza.classificacoesTributarias(), classificacoesTributarias,
                NaturezaDaCarga.CLASSIFICACOES_TRIBUTARIAS);
        casar(natureza.registrosDeNcm(), registrosDeNcm, NaturezaDaCarga.REGISTROS_DE_NCM);
        casar(natureza.itensDeAnexo(), itensDeAnexo, NaturezaDaCarga.ITENS_DE_ANEXO);
        casar(natureza.aliquotas(), aliquotas, NaturezaDaCarga.ALIQUOTAS);

        if (classificacoesTributarias.isEmpty()
                && registrosDeNcm.isEmpty()
                && itensDeAnexo.isEmpty()
                && aliquotas.isEmpty()) {
            throw new CatalogoInvalido(
                    "A carga não tem nenhum registro em nenhuma tabela. Se a intenção era importar um "
                            + "catálogo, confira os caminhos dos arquivos; gravar uma carga vazia só "
                            + "produziria uma auditoria que não avalia nada.");
        }
    }

    /** Quantidade total de registros da carga, somando as quatro tabelas. */
    public int quantidadeDeRegistros() {
        return classificacoesTributarias.size()
                + registrosDeNcm.size()
                + itensDeAnexo.size()
                + aliquotas.size();
    }

    /**
     * Natureza declarada exatamente quando há registro a que ela se refira.
     *
     * <p>Registro sem procedência é o buraco que a coluna obrigatória fecha;
     * procedência sem registro é afirmação sobre conteúdo que não existe. Nenhum
     * dos dois passa daqui.</p>
     */
    private static void casar(
            Optional<Natureza> natureza, List<?> registros, String tabela) {

        if (natureza.isPresent() == registros.isEmpty()) {
            throw new CatalogoInvalido(
                    ("A tabela %s trouxe %d registro(s) e %s natureza declarada. As duas coisas "
                            + "precisam concordar.")
                            .formatted(tabela, registros.size(),
                                    natureza.isPresent() ? "tem" : "não tem"));
        }
    }

    private static <T extends RegistroNormativo> List<T> validar(List<T> registros, String tabela) {
        if (registros == null) {
            throw new CatalogoInvalido(
                    "A lista de %s deve ser vazia quando a carga não traz nenhum registro, nunca nula."
                            .formatted(tabela));
        }
        if (registros.stream().anyMatch(Objects::isNull)) {
            throw new CatalogoInvalido("A lista de %s não pode conter registro nulo.".formatted(tabela));
        }
        recusarVigenciasSobrepostas(registros);
        return List.copyOf(registros);
    }

    private static <T extends RegistroNormativo> void recusarVigenciasSobrepostas(Collection<T> registros) {
        Map<String, List<T>> porChave = new LinkedHashMap<>();
        for (T registro : registros) {
            porChave.computeIfAbsent(registro.chaveDeVigencia(), chave -> new ArrayList<>()).add(registro);
        }
        // A construção da série é a validação: SerieNormativa recusa duas versões
        // do mesmo registro valendo na mesma data. A série montada aqui é
        // descartada de propósito — quem a monta para consulta é o repositório.
        for (Map.Entry<String, List<T>> serie : porChave.entrySet()) {
            new SerieNormativa<>(serie.getKey(), serie.getValue());
        }
    }
}
