package br.edu.tcc.auditoria.infraestrutura.lote;

import br.edu.tcc.auditoria.aplicacao.auditoria.DocumentoComItens;
import br.edu.tcc.auditoria.aplicacao.auditoria.FonteDeLoteDeDocumentos;
import br.edu.tcc.auditoria.aplicacao.auditoria.LoteDeDocumentos;
import br.edu.tcc.auditoria.infraestrutura.xml.FalhaDeLeitura;
import br.edu.tcc.auditoria.infraestrutura.xml.FalhasDeLeituraEmMemoria;
import br.edu.tcc.auditoria.infraestrutura.xml.LeitorLote;

import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

// Classe que lê um lote de documentos de uma pasta ou de um .zip e devolve os documentos já normalizados e pseudonimizados. Arquivo ilegível é registrado e não para o lote, e o resumo da entrada é calculado à parte, contando também os arquivos que não foram lidos.
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
            try (Stream<DocumentoComItens> documentos = leitorLote.ler(origem)) {
                return new LoteDeDocumentos(resumo, documentos.toList());
            }
        } catch (IOException erroDeLeitura) {
            throw new UncheckedIOException(
                    "Não foi possível ler o lote em \"%s\".".formatted(origem), erroDeLeitura);
        }
    }

    // Devolve as falhas da última leitura, na ordem em que aconteceram.
    public List<FalhaDeLeitura> falhasDeLeitura() {
        return falhas.falhas();
    }
}
