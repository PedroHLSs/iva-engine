package br.edu.tcc.auditoria.infraestrutura.catalogo;

import br.edu.tcc.auditoria.aplicacao.catalogo.Natureza;
import br.edu.tcc.auditoria.infraestrutura.csv.LinhaCsv;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * A coluna que todo CSV de catálogo passou a ter obrigatoriamente.
 *
 * <h2>Obrigatória, e por isso impossível de esquecer</h2>
 *
 * <p>Fica ao lado de {@code ProcedenciaEmCsv} pelo mesmo motivo: num lugar só,
 * para que nenhum importador possa deixar de exigi-la. Linha sem natureza recusa
 * o arquivo inteiro — e é essa recusa que faz a marcação valer alguma coisa. Uma
 * coluna opcional teria o mesmo buraco do cabeçalho de comentário: o arquivo
 * fictício de onde alguém tirou a declaração chegaria indistinguível do real.</p>
 *
 * <h2>Um arquivo, uma procedência</h2>
 *
 * <p>Todas as linhas de um mesmo CSV precisam declarar a mesma natureza. Mistura
 * dentro de um arquivo é quase sempre concatenação acidental de dois arquivos, e
 * aceitá-la obrigaria a inventar o que fazer com o conjunto — enquanto a mistura
 * que <em>interessa</em>, a de tabelas diferentes, continua representável e é
 * exatamente o caso "parcialmente fictício".</p>
 */
final class NaturezaEmCsv {

    static final String COLUNA_NATUREZA = "natureza";

    private NaturezaEmCsv() {
    }

    /**
     * A natureza do arquivo, ou vazio se ele não tem linha nenhuma.
     *
     * <p>Arquivo só com cabeçalho é como esta carga declara "não trago registro
     * aqui". Não há linha em que declarar procedência, e inventar uma seria
     * afirmar sobre um conteúdo que não existe.</p>
     */
    static Optional<Natureza> uniforme(List<LinhaCsv> linhas) {
        Natureza daPrimeira = null;
        int numeroDaPrimeira = 0;

        for (LinhaCsv linha : linhas) {
            Natureza declarada = ler(linha);
            if (daPrimeira == null) {
                daPrimeira = declarada;
                numeroDaPrimeira = linha.numero();
            } else if (declarada != daPrimeira) {
                throw new ImportacaoDeCatalogoInvalida(
                        ("Linha %d: natureza \"%s\", diferente da declarada na linha %d (\"%s\"). "
                                + "Um arquivo tem uma procedência só; duas num arquivo costumam ser "
                                + "dois arquivos que foram colados juntos.")
                                .formatted(linha.numero(), declarada.name(),
                                        numeroDaPrimeira, daPrimeira.name()));
            }
        }
        return Optional.ofNullable(daPrimeira);
    }

    private static Natureza ler(LinhaCsv linha) {
        String declarada = linha.textoObrigatorio(COLUNA_NATUREZA);
        return Arrays.stream(Natureza.values())
                .filter(candidata -> candidata.name().equals(declarada))
                .findFirst()
                .orElseThrow(() -> new ImportacaoDeCatalogoInvalida(
                        ("Linha %d: natureza desconhecida \"%s\". Valores aceitos: %s. A coluna diz se "
                                + "o conteúdo é transcrição de fonte normativa ou dado de "
                                + "demonstração, e sem ela a tela não tem como avisar quem lê.")
                                .formatted(linha.numero(), declarada,
                                        Arrays.toString(Natureza.values()))));
    }
}
