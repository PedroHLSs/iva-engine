package br.edu.tcc.auditoria.infraestrutura.xml;

import br.edu.tcc.auditoria.infraestrutura.xml.gerado.TNFe;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LeitorDocumentoFiscalTest {

    private final LeitorDocumentoFiscal leitor = DocumentoDeTeste.leitor();

    @Test
    void deveLerDocumentoComEnvelopeDeAutorizacao() throws IOException {
        try (InputStream conteudo = DocumentoDeTeste.abrir(DocumentoDeTeste.ITEM_COMPLETO)) {
            TNFe documento = leitor.ler(conteudo);

            assertThat(documento.getInfNFe().getId())
                    .as("O envelope <nfeProc> deve ser desembrulhado e o <NFe> de dentro devolvido")
                    .isEqualTo("NFe11111111111111111111111111111111111111111111");
            assertThat(documento.getInfNFe().getDet()).hasSize(1);
        }
    }

    @Test
    void deveLerDocumentoSemEnvelopeDeAutorizacao() throws IOException {
        try (InputStream conteudo = DocumentoDeTeste.abrir(DocumentoDeTeste.ITEM_COM_CAMPOS_AUSENTES)) {
            TNFe documento = leitor.ler(conteudo);

            assertThat(documento.getInfNFe().getId())
                    .isEqualTo("NFe22222222222222222222222222222222222222222222");
        }
    }

    @Test
    void deveRecusarXmlQueNaoEDocumentoFiscal() {
        String outroDocumento = """
                <?xml version="1.0" encoding="UTF-8"?>
                <retConsStatServ xmlns="http://www.portalfiscal.inf.br/nfe" versao="4.00"/>
                """;

        assertThatThrownBy(() -> leitor.ler(DocumentoDeTeste.conteudo(outroDocumento)))
                .isInstanceOf(DocumentoFiscalIlegivel.class)
                .hasMessageContaining("retConsStatServ");
    }

    @Test
    void deveRecusarDocumentoDeOutroNamespace() {
        String forjado = """
                <?xml version="1.0" encoding="UTF-8"?>
                <NFe xmlns="http://exemplo.invalido/nfe"/>
                """;

        assertThatThrownBy(() -> leitor.ler(DocumentoDeTeste.conteudo(forjado)))
                .isInstanceOf(DocumentoFiscalIlegivel.class)
                .hasMessageContaining("namespace");
    }

    @Test
    void deveRecusarXmlMalformado() throws IOException {
        try (InputStream conteudo = DocumentoDeTeste.abrir(DocumentoDeTeste.CORROMPIDO)) {
            assertThatThrownBy(() -> leitor.ler(conteudo))
                    .isInstanceOf(DocumentoFiscalIlegivel.class);
        }
    }

    @Test
    void deveRecusarConteudoSemElementoNenhum() {
        assertThatThrownBy(() -> leitor.ler(DocumentoDeTeste.conteudo("")))
                .isInstanceOf(DocumentoFiscalIlegivel.class);
    }

    /**
     * O sistema lê arquivo que veio de fora. Um documento que declare entidade
     * externa não pode fazer o processo abrir arquivo do disco de quem roda a
     * auditoria.
     */
    @Test
    void naoDeveResolverEntidadeExternaDeclaradaNoDocumento() {
        String comEntidadeExterna = """
                <?xml version="1.0" encoding="UTF-8"?>
                <!DOCTYPE NFe [<!ENTITY vazamento SYSTEM "file:///etc/passwd">]>
                <NFe xmlns="http://www.portalfiscal.inf.br/nfe">&vazamento;</NFe>
                """;

        assertThatThrownBy(() -> leitor.ler(DocumentoDeTeste.conteudo(comEntidadeExterna)))
                .isInstanceOf(DocumentoFiscalIlegivel.class);
    }

    @Test
    void deveRecusarLeituraSemConteudo() {
        assertThatThrownBy(() -> leitor.ler(null))
                .isInstanceOf(DocumentoFiscalIlegivel.class);
    }
}
