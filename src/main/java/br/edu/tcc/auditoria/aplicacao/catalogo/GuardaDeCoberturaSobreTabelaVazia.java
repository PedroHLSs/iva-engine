package br.edu.tcc.auditoria.aplicacao.catalogo;

import br.edu.tcc.auditoria.dominio.PeriodoVigencia;
import br.edu.tcc.auditoria.dominio.catalogo.AnexoDeclarado;
import br.edu.tcc.auditoria.dominio.catalogo.ItemAnexo;
import br.edu.tcc.auditoria.dominio.catalogo.TipoDeCodigoDoAnexo;
import br.edu.tcc.auditoria.dominio.excecao.CatalogoInvalido;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

// Classe que recusa carga que declara cobertura sobre tabela sem nenhum registro. Dentro da cobertura, registro ausente vira apontamento (D004); com a tabela vazia, todo item do período seria apontado com base em silêncio. É o mesmo critério de "natureza declarada só em tabela com registro", aplicado à cobertura. Acrescentada na Etapa 12.
// Emenda de 03/10/2026: passou a conferir também por anexo. Anexo declarado carregado em anexos-declarados.csv, de NCM ou de NCM e NBS, precisa de ao menos uma linha em item-anexo.csv vigente no período de carregamento. Sem isso, a tabela de itens de anexo passava por não estar vazia — por causa de outro anexo —, e a R03 apontava todo NCM de código que admite o anexo vazio. Anexo de NBS carregado e vazio continua aceito (D7), e anexo sem vigência continua servindo só para validar identificador (D6, D9).
public final class GuardaDeCoberturaSobreTabelaVazia {

    // Construtor privado: ninguém cria objeto desta classe, só usa o método estático.
    private GuardaDeCoberturaSobreTabelaVazia() {
    }

    // Método estático que confere as três tabelas com cobertura declarada e recusa, numa mensagem só, todas as que vieram vazias.
    public static void exigir(CargaDeCatalogo carga) {
        List<String> vazias = new ArrayList<>();
        if (carga.classificacoesTributarias().isEmpty()) {
            vazias.add(TabelaNormativa.CLASSIFICACAO_TRIBUTARIA.name());
        }
        if (carga.registrosDeNcm().isEmpty()) {
            vazias.add(TabelaNormativa.NCM.name());
        }
        if (carga.itensDeAnexo().isEmpty()) {
            vazias.add(TabelaNormativa.ITEM_ANEXO.name());
        }
        if (!vazias.isEmpty()) {
            throw new CatalogoInvalido(
                    ("A carga \"%s\" declara cobertura sobre tabela sem nenhum registro: %s. Dentro do "
                            + "período coberto, registro que não está no catálogo vira apontamento; com a "
                            + "tabela vazia, todo item daquele período seria apontado por silêncio do "
                            + "catálogo, e não por divergência. Forneça os registros dessa tabela.")
                            .formatted(carga.versao(), String.join(", ", vazias)));
        }
        exigirLinhaPorAnexoCarregado(carga);
    }

    // Método auxiliar que recusa, numa mensagem só, todo anexo de NCM declarado carregado sem nenhuma linha de item-anexo.csv vigente no período de carregamento.
    private static void exigirLinhaPorAnexoCarregado(CargaDeCatalogo carga) {
        List<String> semLinha = new ArrayList<>();
        for (AnexoDeclarado anexo : carga.cobertura().anexosDeclarados()) {
            if (anexo.carregamento().isEmpty() || anexo.tipoDeCodigo() == TipoDeCodigoDoAnexo.NBS) {
                continue;
            }
            PeriodoVigencia carregamento = anexo.carregamento().get();
            boolean temLinha = carga.itensDeAnexo().stream()
                    .filter(item -> item.identificadorDoAnexo().equals(anexo.identificador()))
                    .map(ItemAnexo::vigencia)
                    .anyMatch(vigencia -> sobrepoem(vigencia, carregamento));
            if (!temLinha) {
                semLinha.add("\"%s\" (%s, declarado carregado a partir de %s%s)".formatted(
                        anexo.identificador().valor(),
                        anexo.tipoDeCodigo(),
                        carregamento.inicio(),
                        carregamento.fim().map(" até %s"::formatted).orElse("")));
            }
        }
        if (!semLinha.isEmpty()) {
            throw new CatalogoInvalido(
                    ("A carga \"%s\" declara em anexos-declarados.csv anexo carregado sem nenhuma linha em "
                            + "item-anexo.csv vigente no período de carregamento: %s. Anexo declarado carregado "
                            + "quer dizer que o item-anexo.csv o traz por completo; vazio, todo NCM de código "
                            + "que admite esse anexo seria apontado por silêncio do catálogo. Forneça as linhas "
                            + "do anexo, ou deixe a vigência dele em branco em anexos-declarados.csv.")
                            .formatted(carga.versao(), String.join("; ", semLinha)));
        }
    }

    // Método auxiliar que diz se dois períodos têm ao menos um dia em comum; fim vazio é período aberto.
    private static boolean sobrepoem(PeriodoVigencia um, PeriodoVigencia outro) {
        LocalDate fimDeUm = um.fim().orElse(LocalDate.MAX);
        LocalDate fimDoOutro = outro.fim().orElse(LocalDate.MAX);
        return !um.inicio().isAfter(fimDoOutro) && !outro.inicio().isAfter(fimDeUm);
    }
}
