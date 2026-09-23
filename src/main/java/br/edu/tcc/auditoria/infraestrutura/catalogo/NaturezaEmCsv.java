package br.edu.tcc.auditoria.infraestrutura.catalogo;

import br.edu.tcc.auditoria.aplicacao.catalogo.Natureza;
import br.edu.tcc.auditoria.infraestrutura.csv.LinhaCsv;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

// Classe que lê a coluna natureza, obrigatória em todo CSV de dados do catálogo, com FICTICIO ou NORMATIVO. Linha sem natureza recusa o arquivo, e todas as linhas de um arquivo precisam declarar a mesma.
final class NaturezaEmCsv {

    static final String COLUNA_NATUREZA = "natureza";

    // Construtor privado: ninguém cria objeto desta classe, só usa os métodos estáticos.
    private NaturezaEmCsv() {
    }

    // Método estático que devolve a natureza do arquivo, ou vazio se ele só tem cabeçalho; recusa se duas linhas declararem naturezas diferentes.
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

    // Método auxiliar que lê a natureza de uma linha; recusa valor desconhecido.
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
