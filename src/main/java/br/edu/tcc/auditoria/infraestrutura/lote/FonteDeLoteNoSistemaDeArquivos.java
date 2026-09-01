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

/**
 * Lê um lote de documentos de um diretório ou de um pacote ZIP.
 *
 * <p>Adapta o leitor de lote da etapa 4 à porta que a aplicação declara. O
 * leiaute da NF-e não passa por aqui: entram caminhos, saem documentos de
 * domínio já normalizados e pseudonimizados.</p>
 *
 * <h2>A origem é lida duas vezes, de propósito</h2>
 *
 * <p>Uma passada calcula o resumo do conjunto de arquivos; outra produz os
 * documentos. Derivar o resumo dos documentos lidos não serviria: ele precisa
 * descrever o que foi apresentado ao sistema, incluindo os arquivos que não
 * puderam ser lidos, e precisa ser o mesmo mesmo que a leitura mude.</p>
 *
 * <h2>Arquivo ilegível não interrompe o lote</h2>
 *
 * <p>Falhas são registradas e ficam disponíveis para quem chamou. Um XML
 * corrompido no meio de mil não pode impedir a auditoria dos demais — mas
 * também não pode sumir sem deixar rastro.</p>
 */
@Component
public class FonteDeLoteNoSistemaDeArquivos implements FonteDeLoteDeDocumentos {

    private final LeitorLote leitorLote;
    private final FalhasDeLeituraEmMemoria falhas;

    public FonteDeLoteNoSistemaDeArquivos(LeitorLote leitorLote, FalhasDeLeituraEmMemoria falhas) {
        if (leitorLote == null || falhas == null) {
            throw new IllegalArgumentException(
                    "A fonte de lote exige o leitor de lote e o registro de falhas de leitura.");
        }
        this.leitorLote = leitorLote;
        this.falhas = falhas;
    }

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

    /** Falhas registradas na última leitura, na ordem em que ocorreram. */
    public List<FalhaDeLeitura> falhasDeLeitura() {
        return falhas.falhas();
    }
}
