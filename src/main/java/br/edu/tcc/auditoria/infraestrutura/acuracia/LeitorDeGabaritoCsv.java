package br.edu.tcc.auditoria.infraestrutura.acuracia;

import br.edu.tcc.auditoria.aplicacao.acuracia.AvaliacaoDeAcuraciaInvalida;
import br.edu.tcc.auditoria.aplicacao.acuracia.EnderecoDaAvaliacao;
import br.edu.tcc.auditoria.aplicacao.acuracia.FonteDeGabarito;
import br.edu.tcc.auditoria.aplicacao.acuracia.Gabarito;
import br.edu.tcc.auditoria.aplicacao.acuracia.LinhaDeGabarito;
import br.edu.tcc.auditoria.dominio.ChaveAcesso;
import br.edu.tcc.auditoria.dominio.acuracia.RotuloEsperado;
import br.edu.tcc.auditoria.infraestrutura.csv.LeitorCsv;
import br.edu.tcc.auditoria.infraestrutura.csv.LinhaCsv;

import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Lê o gabarito rotulado à mão de um arquivo CSV.
 *
 * <p>Quatro colunas obrigatórias: {@code chave_documento}, {@code numero_item},
 * {@code regra_id} e {@code rotulo_esperado}. O separador é {@code ;}, a
 * codificação é UTF-8, linhas iniciadas por {@code #} são comentário — as mesmas
 * convenções dos CSVs de catálogo, porque o mesmo leitor as implementa.</p>
 *
 * <h2>Nada é presumido</h2>
 *
 * <p>Todas as quatro colunas são obrigatórias em toda linha. Campo em branco não
 * vira valor padrão, item ausente não vira item 1 e rótulo ausente não vira
 * {@code CONFORME} — que seria o defeito mais grave possível aqui, porque
 * inflaria os verdadeiros negativos e, com eles, toda métrica derivada.</p>
 *
 * <p>A chave de acesso passa pela validação do domínio: 44 dígitos, sem espaço.
 * Uma chave truncada num gabarito produziria dezenas de linhas sem avaliação e
 * um relatório acusando o acervo, em vez do arquivo.</p>
 */
@Component
public class LeitorDeGabaritoCsv implements FonteDeGabarito {

    public static final String COLUNA_CHAVE_DOCUMENTO = "chave_documento";
    public static final String COLUNA_NUMERO_ITEM = "numero_item";
    public static final String COLUNA_REGRA_ID = "regra_id";
    public static final String COLUNA_ROTULO_ESPERADO = "rotulo_esperado";

    /** As colunas esperadas, para a mensagem de modo de usar do comando. */
    public static String colunasEsperadas() {
        return String.join("; ",
                COLUNA_CHAVE_DOCUMENTO, COLUNA_NUMERO_ITEM, COLUNA_REGRA_ID, COLUNA_ROTULO_ESPERADO);
    }

    @Override
    public Gabarito carregar(Path arquivo) {
        if (arquivo == null) {
            throw new GabaritoInvalido("Não foi informado o arquivo do gabarito.");
        }
        if (!Files.isRegularFile(arquivo)) {
            throw new GabaritoInvalido(
                    ("Não há arquivo de gabarito em \"%s\". O gabarito é rotulado à mão e informado por "
                            + "quem mede; o sistema não gera nenhum.").formatted(arquivo));
        }
        try (Reader origem = Files.newBufferedReader(arquivo, StandardCharsets.UTF_8)) {
            return ler(origem);
        } catch (IOException falhaDeLeitura) {
            throw new UncheckedIOException(
                    "Falha ao ler o gabarito \"%s\".".formatted(arquivo), falhaDeLeitura);
        }
    }

    /** Lê o gabarito de um {@link Reader} já aberto. */
    public Gabarito ler(Reader origem) throws IOException {
        List<LinhaDeGabarito> linhas = LeitorCsv.ler(origem, GabaritoInvalido::new).stream()
                .map(LeitorDeGabaritoCsv::converter)
                .toList();
        try {
            return new Gabarito(linhas);
        } catch (AvaliacaoDeAcuraciaInvalida recusa) {
            // A recusa por linha repetida já nomeia as duas linhas físicas: aqui só
            // se troca o tipo, para que a linha de comando trate gabarito malformado
            // como o arquivo que ele é, e não como defeito de quem chamou.
            throw new GabaritoInvalido(recusa.getMessage(), recusa);
        }
    }

    private static LinhaDeGabarito converter(LinhaCsv linha) {
        String chave = linha.textoObrigatorio(COLUNA_CHAVE_DOCUMENTO);
        int numeroItem = linha.inteiroObrigatorio(COLUNA_NUMERO_ITEM);
        String regraId = linha.textoObrigatorio(COLUNA_REGRA_ID);
        String rotulo = linha.textoObrigatorio(COLUNA_ROTULO_ESPERADO);

        // Conferido aqui, e não deixado para o construtor do endereço, porque a
        // recusa da aplicação não carrega o número da linha física — e num arquivo
        // rotulado à mão o número da linha é metade da mensagem útil.
        if (numeroItem < 1) {
            throw new GabaritoInvalido(
                    "Linha %d: a coluna \"%s\" é o número do item, contado a partir de 1, mas veio %d."
                            .formatted(linha.numero(), COLUNA_NUMERO_ITEM, numeroItem));
        }

        EnderecoDaAvaliacao endereco = linha.converterCom(() ->
                new EnderecoDaAvaliacao(new ChaveAcesso(chave), numeroItem, regraId));
        RotuloEsperado esperado = linha.converterCom(() -> RotuloEsperado.de(rotulo));

        return new LinhaDeGabarito(linha.numero(), endereco, esperado);
    }
}
