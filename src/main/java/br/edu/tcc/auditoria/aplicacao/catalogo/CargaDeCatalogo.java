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

// Representa um catálogo normativo completo, pronto para ser gravado, com a cobertura e a natureza declaradas por quem o importou.
public record CargaDeCatalogo(
        String versao,
        CoberturaDoCatalogo cobertura,
        NaturezaDaCarga natureza,
        List<ClassificacaoTributaria> classificacoesTributarias,
        List<RegistroNcm> registrosDeNcm,
        List<ItemAnexo> itensDeAnexo,
        List<AliquotaVigente> aliquotas) {

    // Valida a carga: exige versão, cobertura e natureza, confere cada tabela e recusa carga sem nenhum registro.
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

    public int quantidadeDeRegistros() {
        return classificacoesTributarias.size()
                + registrosDeNcm.size()
                + itensDeAnexo.size()
                + aliquotas.size();
    }

    // Método auxiliar para garantir que a tabela tenha natureza declarada exatamente quando tem registros.
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

    // Método auxiliar para validar a lista de registros de uma tabela, recusando lista nula, elemento nulo e vigências sobrepostas.
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

    // Método auxiliar para recusar registros de mesma chave cujas vigências se sobrepõem.
    private static <T extends RegistroNormativo> void recusarVigenciasSobrepostas(Collection<T> registros) {
        Map<String, List<T>> porChave = new LinkedHashMap<>();
        for (T registro : registros) {
            porChave.computeIfAbsent(registro.chaveDeVigencia(), chave -> new ArrayList<>()).add(registro);
        }
        // Montar a SerieNormativa já é a validação: ela recusa duas versões do mesmo registro valendo na mesma data.
        for (Map.Entry<String, List<T>> serie : porChave.entrySet()) {
            new SerieNormativa<>(serie.getKey(), serie.getValue());
        }
    }
}
