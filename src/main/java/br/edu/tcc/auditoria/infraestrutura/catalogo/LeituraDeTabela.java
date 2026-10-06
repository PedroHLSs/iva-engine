package br.edu.tcc.auditoria.infraestrutura.catalogo;

import br.edu.tcc.auditoria.aplicacao.catalogo.Natureza;
import br.edu.tcc.auditoria.dominio.excecao.ExcecaoDeDominio;
import br.edu.tcc.auditoria.infraestrutura.csv.LeitorCsv;
import br.edu.tcc.auditoria.infraestrutura.csv.LinhaCsv;

import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

// Classe que lê um CSV de dados do catálogo linha a linha sem parar no primeiro erro: cada linha recusada vai para as RecusasDaCarga, e a leitura continua. A natureza e o conteúdo de cada linha são conferidos em separado, para os dois erros aparecerem se a linha tiver os dois. Acrescentada na Etapa 12.
final class LeituraDeTabela {

    // Construtor privado: ninguém cria objeto desta classe, só usa o método estático.
    private LeituraDeTabela() {
    }

    // Método estático que lê o arquivo e devolve a tabela; se houver qualquer recusa no arquivo, devolve a tabela vazia, porque a carga inteira vai ser recusada no fim.
    static <T> TabelaImportada<T> ler(
            Reader origem, String arquivo, Function<LinhaCsv, T> conversao, RecusasDaCarga recusas)
            throws IOException {

        List<LinhaCsv> linhas;
        try {
            linhas = LeitorCsv.ler(origem, RecusaDoCatalogoEmCsv.INSTANCIA);
        } catch (ImportacaoDeCatalogoInvalida arquivoMalformado) {
            recusas.registrar(arquivo, arquivoMalformado);
            return vazia();
        }

        List<T> registros = new ArrayList<>();
        Natureza daPrimeira = null;
        int numeroDaPrimeira = 0;

        for (LinhaCsv linha : linhas) {
            Optional<Natureza> natureza = lerNatureza(linha, arquivo, recusas);
            if (natureza.isPresent()) {
                if (daPrimeira == null) {
                    daPrimeira = natureza.get();
                    numeroDaPrimeira = linha.numero();
                } else if (natureza.get() != daPrimeira) {
                    recusas.registrarDaLinha(arquivo, linha.numero(),
                            Optional.of(NaturezaEmCsv.COLUNA_NATUREZA),
                            Optional.of(linha.valorComoVeio(NaturezaEmCsv.COLUNA_NATUREZA)),
                            ("natureza \"%s\", diferente da declarada na linha %d (\"%s\"). Um arquivo "
                                    + "tem uma procedência só; duas num arquivo costumam ser dois arquivos "
                                    + "que foram colados juntos.")
                                    .formatted(natureza.get().name(), numeroDaPrimeira, daPrimeira.name()));
                }
            }

            try {
                registros.add(conversao.apply(linha));
            } catch (ImportacaoDeCatalogoInvalida | ExcecaoDeDominio recusada) {
                recusas.registrar(arquivo, comLinha(recusada, linha));
            }
        }

        if (recusas.temRecusaEm(arquivo)) {
            return vazia();
        }
        return new TabelaImportada<>(registros, Optional.ofNullable(daPrimeira));
    }

    // Método auxiliar que lê a natureza de uma linha, registrando a recusa com a coluna e o valor.
    private static Optional<Natureza> lerNatureza(LinhaCsv linha, String arquivo, RecusasDaCarga recusas) {
        try {
            return NaturezaEmCsv.uniforme(List.of(linha));
        } catch (ImportacaoDeCatalogoInvalida recusada) {
            recusas.registrar(arquivo, recusada,
                    Optional.of(NaturezaEmCsv.COLUNA_NATUREZA),
                    Optional.of(linha.valorComoVeio(NaturezaEmCsv.COLUNA_NATUREZA)));
            return Optional.empty();
        }
    }

    // Método auxiliar que garante o número da linha na mensagem de uma recusa do domínio que escapou sem ele.
    private static RuntimeException comLinha(RuntimeException recusada, LinhaCsv linha) {
        if (recusada instanceof ImportacaoDeCatalogoInvalida) {
            return recusada;
        }
        return new ImportacaoDeCatalogoInvalida(
                "Linha %d: %s".formatted(linha.numero(), recusada.getMessage()), recusada);
    }

    // Método auxiliar que devolve a tabela vazia usada quando o arquivo teve recusa.
    private static <T> TabelaImportada<T> vazia() {
        return new TabelaImportada<>(List.of(), Optional.empty());
    }
}
