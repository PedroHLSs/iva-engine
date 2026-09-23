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

// Classe que lê o gabarito rotulado à mão de um CSV com quatro colunas: chave_documento, numero_item, regra_id e rotulo_esperado. As quatro são obrigatórias em toda linha: nada vira valor padrão, e rótulo em branco nunca vira CONFORME, porque isso inflaria as métricas.
@Component
public class LeitorDeGabaritoCsv implements FonteDeGabarito {

    public static final String COLUNA_CHAVE_DOCUMENTO = "chave_documento";
    public static final String COLUNA_NUMERO_ITEM = "numero_item";
    public static final String COLUNA_REGRA_ID = "regra_id";
    public static final String COLUNA_ROTULO_ESPERADO = "rotulo_esperado";

    // Método estático que devolve os nomes das colunas, para a mensagem de ajuda do comando.
    public static String colunasEsperadas() {
        return String.join("; ",
                COLUNA_CHAVE_DOCUMENTO, COLUNA_NUMERO_ITEM, COLUNA_REGRA_ID, COLUNA_ROTULO_ESPERADO);
    }

    // Abre o arquivo do gabarito e lê as linhas; recusa se o arquivo não existir.
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

    // Lê o gabarito de um Reader já aberto.
    public Gabarito ler(Reader origem) throws IOException {
        List<LinhaDeGabarito> linhas = LeitorCsv.ler(origem, GabaritoInvalido::new).stream()
                .map(LeitorDeGabaritoCsv::converter)
                .toList();
        try {
            return new Gabarito(linhas);
        } catch (AvaliacaoDeAcuraciaInvalida recusa) {
            // Só troca o tipo do erro, para o comando tratar como gabarito malformado; a mensagem já diz quais linhas se repetem.
            throw new GabaritoInvalido(recusa.getMessage(), recusa);
        }
    }

    // Método auxiliar que transforma uma linha do CSV numa linha do gabarito, conferindo a chave de acesso e o rótulo.
    private static LinhaDeGabarito converter(LinhaCsv linha) {
        String chave = linha.textoObrigatorio(COLUNA_CHAVE_DOCUMENTO);
        int numeroItem = linha.inteiroObrigatorio(COLUNA_NUMERO_ITEM);
        String regraId = linha.textoObrigatorio(COLUNA_REGRA_ID);
        String rotulo = linha.textoObrigatorio(COLUNA_ROTULO_ESPERADO);

        // Confere aqui, e não no endereço, para a mensagem de erro dizer o número da linha do arquivo.
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
