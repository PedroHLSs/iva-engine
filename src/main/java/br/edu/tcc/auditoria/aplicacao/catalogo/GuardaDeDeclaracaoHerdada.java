package br.edu.tcc.auditoria.aplicacao.catalogo;

import br.edu.tcc.auditoria.dominio.catalogo.AnexoDeclarado;
import br.edu.tcc.auditoria.dominio.excecao.CatalogoInvalido;

import java.util.ArrayList;
import java.util.List;

// Classe que recusa trocar uma tabela e herdar a declaração que fala dela. Acrescentada em 04/10/2026 (D026, decisão D-a), para o "Editar" e para a importação parcial.
// A cobertura declara o período e a fonte de cada tabela, e dentro desse período registro ausente vira apontamento (D004). Herdada sobre uma tabela nova, ela afirmaria para a tabela nova o que alguém declarou para a antiga, e a R06 apontaria com base nisso. Por isso trocar classificacao-tributaria.csv, registro-ncm.csv ou item-anexo.csv exige cobertura.csv junto.
// O anexos-declarados.csv diz quais anexos o item-anexo.csv traz por completo. Trocar o item-anexo.csv e herdar essa lista afirmaria a completude da tabela nova. Por isso, quando a lista herdada declara algum anexo carregado, trocar item-anexo.csv exige anexos-declarados.csv junto. A GuardaDeCoberturaSobreTabelaVazia só pega o anexo carregado sem nenhuma linha; o anexo com linhas, mas incompleto, ela não tem como ver.
public final class GuardaDeDeclaracaoHerdada {

    // Construtor privado: ninguém cria objeto desta classe, só usa o método estático.
    private GuardaDeDeclaracaoHerdada() {
    }

    // Método estático que confere a substituição contra a carga de origem e recusa, numa mensagem só, toda declaração que seria herdada sobre tabela trocada.
    public static void exigir(SubstituicaoDeTabelas substituicao, CargaDeCatalogo origem) {
        if (substituicao == null || origem == null) {
            throw new CatalogoInvalido(
                    "A guarda da declaração herdada precisa das tabelas novas e da carga de origem.");
        }
        List<String> problemas = new ArrayList<>();

        if (substituicao.cobertura().isEmpty()) {
            List<String> trocadas = new ArrayList<>();
            substituicao.classificacoesTributarias().ifPresent(tabela -> trocadas.add("classificacao-tributaria.csv"));
            substituicao.registrosDeNcm().ifPresent(tabela -> trocadas.add("registro-ncm.csv"));
            substituicao.itensDeAnexo().ifPresent(tabela -> trocadas.add("item-anexo.csv"));
            if (!trocadas.isEmpty()) {
                problemas.add(("Trocar %s exige cobertura.csv junto. A cobertura herdada declararia para a "
                        + "tabela nova o período e a fonte da tabela antiga, e dentro desse período registro "
                        + "que não está no catálogo vira apontamento.").formatted(String.join(", ", trocadas)));
            }
        }

        if (substituicao.itensDeAnexo().isPresent() && substituicao.anexosDeclarados().isEmpty()) {
            List<String> carregados = origem.cobertura().anexosDeclarados().stream()
                    .filter(anexo -> anexo.carregamento().isPresent())
                    .map(AnexoDeclarado::identificador)
                    .map(identificador -> "\"" + identificador.valor() + "\"")
                    .sorted()
                    .toList();
            if (!carregados.isEmpty()) {
                problemas.add(("Trocar item-anexo.csv exige anexos-declarados.csv junto, porque a lista "
                        + "herdada declara carregado por completo: %s. Herdada, ela afirmaria que o "
                        + "item-anexo.csv novo traz esses anexos inteiros.").formatted(String.join(", ", carregados)));
            }
        }

        if (!problemas.isEmpty()) {
            throw new CatalogoInvalido(String.join(" ", problemas) + " Nada foi gravado.");
        }
    }
}
