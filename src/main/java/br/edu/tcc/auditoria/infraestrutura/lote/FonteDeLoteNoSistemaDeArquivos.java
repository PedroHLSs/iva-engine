package br.edu.tcc.auditoria.infraestrutura.lote;

import br.edu.tcc.auditoria.aplicacao.auditoria.DocumentoComItens;
import br.edu.tcc.auditoria.aplicacao.auditoria.FonteDeLoteDeDocumentos;
import br.edu.tcc.auditoria.aplicacao.auditoria.LoteDeDocumentos;
import br.edu.tcc.auditoria.infraestrutura.xml.DocumentoLido;
import br.edu.tcc.auditoria.infraestrutura.xml.FalhaDeLeitura;
import br.edu.tcc.auditoria.infraestrutura.xml.FalhasDeLeituraEmMemoria;
import br.edu.tcc.auditoria.infraestrutura.xml.LeitorLote;

import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

// Classe que lê um lote de documentos de uma pasta ou de um .zip e devolve os documentos já normalizados e pseudonimizados. Arquivo ilegível é registrado e não para o lote, e o resumo da entrada é calculado à parte, contando também os arquivos que não foram lidos.
// Emenda de 04/10/2026 (D019): dois arquivos com a mesma chave de acesso — o caso comum é o -nfe.xml e o -procNFe.xml da mesma nota — são resolvidos aqui, antes do motor. Conteúdo idêntico vira um documento só, e a cópia é contada em documentosRepetidosDescartados. Conteúdo diferente é conflito: nenhum dos arquivos entra, e cada um é registrado como falha, com o motivo, porque ficar com um seria decidir qual é o verdadeiro. Até essa data os dois entravam, o recibo contava dois e o banco guardava o último.
@Component
public class FonteDeLoteNoSistemaDeArquivos implements FonteDeLoteDeDocumentos {

    private final LeitorLote leitorLote;
    private final FalhasDeLeituraEmMemoria falhas;

    // Construtor que recebe o leitor de lote e o registro de falhas.
    public FonteDeLoteNoSistemaDeArquivos(LeitorLote leitorLote, FalhasDeLeituraEmMemoria falhas) {
        if (leitorLote == null || falhas == null) {
            throw new IllegalArgumentException(
                    "A fonte de lote exige o leitor de lote e o registro de falhas de leitura.");
        }
        this.leitorLote = leitorLote;
        this.falhas = falhas;
    }

    // Abre a origem, calcula o resumo da entrada e lê os documentos; recusa se a origem não existir.
    @Override
    public LoteDeDocumentos abrir(Path origem) {
        if (origem == null) {
            throw new IllegalArgumentException("Não há origem de lote a abrir.");
        }
        if (!Files.exists(origem)) {
            throw new OrigemDeLoteInexistente(
                    "Não existe diretório nem arquivo em \"%s\".".formatted(origem));
        }

        try {
            String resumo = ResumoDaEntrada.de(origem);
            try (Stream<DocumentoLido> lidos = leitorLote.lerComOrigem(origem)) {
                return semChaveRepetida(resumo, lidos.toList());
            }
        } catch (IOException erroDeLeitura) {
            throw new UncheckedIOException(
                    "Não foi possível ler o lote em \"%s\".".formatted(origem), erroDeLeitura);
        }
    }

    static final String TIPO_DO_CONFLITO = "ChaveDeAcessoComConteudoDivergente";

    // Método auxiliar que agrupa os documentos pela chave de acesso, na ordem de leitura: cópia idêntica é descartada e contada, e chave com conteúdo divergente deixa todos os arquivos dela de fora, registrados como falha.
    private LoteDeDocumentos semChaveRepetida(String resumo, List<DocumentoLido> lidos) {
        Map<String, List<DocumentoLido>> porChave = new LinkedHashMap<>();
        for (DocumentoLido lido : lidos) {
            porChave.computeIfAbsent(lido.documento().documento().chaveAcesso().valor(),
                    chave -> new ArrayList<>()).add(lido);
        }

        List<DocumentoComItens> documentos = new ArrayList<>();
        int repetidos = 0;
        for (List<DocumentoLido> mesmaChave : porChave.values()) {
            DocumentoComItens primeiro = mesmaChave.get(0).documento();
            if (mesmaChave.stream().allMatch(lido -> mesmoConteudo(primeiro, lido.documento()))) {
                documentos.add(primeiro);
                repetidos += mesmaChave.size() - 1;
                continue;
            }
            for (DocumentoLido lido : mesmaChave) {
                falhas.registrar(new FalhaDeLeitura(lido.origem(), TIPO_DO_CONFLITO, motivoDoConflito(mesmaChave.size())));
            }
        }
        return new LoteDeDocumentos(resumo, documentos, repetidos);
    }

    // Método auxiliar que compara dois documentos de mesma chave pelo que o sistema leu deles: o documento normalizado e todos os itens, em ordem de número.
    private static boolean mesmoConteudo(DocumentoComItens um, DocumentoComItens outro) {
        return um.documento().equals(outro.documento()) && um.itensOrdenados().equals(outro.itensOrdenados());
    }

    // Método auxiliar que escreve o motivo do conflito. Não cita o nome dos outros arquivos, que podem trazer a chave de acesso e, dentro dela, o CNPJ do emitente.
    private static String motivoDoConflito(int arquivosComAChave) {
        return ("O arquivo foi lido, mas %d arquivos deste lote têm a mesma chave de acesso e conteúdo "
                + "diferente. Nenhum deles foi auditado nem gravado: ficar com um seria decidir qual é o "
                + "verdadeiro. Os outros aparecem nesta mesma lista.").formatted(arquivosComAChave);
    }

    // Devolve as falhas da última leitura, na ordem em que aconteceram.
    public List<FalhaDeLeitura> falhasDeLeitura() {
        return falhas.falhas();
    }
}
