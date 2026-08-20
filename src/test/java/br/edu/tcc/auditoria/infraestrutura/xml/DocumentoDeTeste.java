package br.edu.tcc.auditoria.infraestrutura.xml;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

/**
 * Acesso aos documentos sintéticos de {@code src/test/resources/documentos} e
 * aos valores fictícios que eles declaram.
 *
 * <p>Nenhum valor aqui é real. Os CNPJ e o CPF são sequências de um dígito só, o
 * NCM é {@code 00000000}, os códigos tributários são {@code 999}/{@code 999999}
 * e os valores são variações de {@code 99,99}. Nada disso afirma coisa alguma
 * sobre a legislação nem se parece com documento de contribuinte.</p>
 */
final class DocumentoDeTeste {

    static final String ITEM_COMPLETO = "nfe-item-completo.xml";
    static final String ITEM_COM_CAMPOS_AUSENTES = "nfe-item-com-campos-ausentes.xml";
    static final String NFCE_SEM_DESTINATARIO = "nfce-consumidor-nao-identificado.xml";
    static final String MULTIPLOS_ITENS = "nfe-multiplos-itens.xml";
    static final String CORROMPIDO = "documento-corrompido.xml";

    static final String CNPJ_DO_EMITENTE = "99999999999999";
    static final String CNPJ_DO_DESTINATARIO = "88888888888888";
    static final String CPF_DO_DESTINATARIO = "77777777777";

    /** Sal fictício, longo o bastante para ser aceito, e obviamente não secreto. */
    static final String SAL_FICTICIO = "sal-ficticio-de-teste-aaaaaaaaaaaaaaaaaaaa";

    private DocumentoDeTeste() {
    }

    static InputStream abrir(String nome) {
        InputStream conteudo = DocumentoDeTeste.class.getResourceAsStream("/documentos/" + nome);
        if (conteudo == null) {
            throw new IllegalStateException("Documento de teste não encontrado no classpath: /documentos/" + nome);
        }
        return conteudo;
    }

    static InputStream conteudo(String xml) {
        return new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8));
    }

    static Path caminho(String nome) {
        URL recurso = DocumentoDeTeste.class.getResource("/documentos/" + nome);
        if (recurso == null) {
            throw new IllegalStateException("Documento de teste não encontrado no classpath: /documentos/" + nome);
        }
        try {
            return Path.of(recurso.toURI());
        } catch (URISyntaxException erro) {
            throw new IllegalStateException("Caminho inválido para o documento de teste " + nome, erro);
        }
    }

    static LeitorDocumentoFiscal leitor() {
        return new LeitorDocumentoFiscal();
    }

    static Pseudonimizador pseudonimizador() {
        return new Pseudonimizador(new SalDeInstalacao(SAL_FICTICIO));
    }

    static NormalizadorDocumento normalizador() {
        return new NormalizadorDocumento(pseudonimizador());
    }
}
