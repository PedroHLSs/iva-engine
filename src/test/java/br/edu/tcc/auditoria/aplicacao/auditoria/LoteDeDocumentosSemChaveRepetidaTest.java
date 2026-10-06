package br.edu.tcc.auditoria.aplicacao.auditoria;

import br.edu.tcc.auditoria.dominio.Documento;
import br.edu.tcc.auditoria.dominio.ItemDocumento;
import br.edu.tcc.auditoria.dominio.regras.CenarioFicticio;
import br.edu.tcc.auditoria.dominio.regras.ConstrutorDeItem;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// D019 (04/10/2026): o lote não aceita dois documentos com a mesma chave de acesso, venha de qual fonte vier. É a garantia de que o recibo não conta o que o banco não guarda. Dados fictícios do CenarioFicticio.
class LoteDeDocumentosSemChaveRepetidaTest {

    private static final String HASH_DA_ENTRADA = "a".repeat(64);

    @Test
    void deveRecusarDoisDocumentosComAMesmaChave() {
        DocumentoComItens um = documentoComUmItem();
        DocumentoComItens outro = documentoComUmItem();

        assertThatThrownBy(() -> new LoteDeDocumentos(HASH_DA_ENTRADA, List.of(um, outro)))
                .isInstanceOf(AuditoriaInvalida.class)
                .hasMessageContaining("mesma chave de acesso");
    }

    @Test
    void aMensagemNaoDeveRepetirAChave() {
        DocumentoComItens um = documentoComUmItem();
        String chave = um.documento().chaveAcesso().valor();

        assertThatThrownBy(() -> new LoteDeDocumentos(HASH_DA_ENTRADA, List.of(um, documentoComUmItem())))
                .message().doesNotContain(chave);
    }

    @Test
    void deveGuardarQuantasCopiasForamDescartadas() {
        LoteDeDocumentos lote = new LoteDeDocumentos(HASH_DA_ENTRADA, List.of(documentoComUmItem()), 1);

        assertThat(lote.documentos()).hasSize(1);
        assertThat(lote.documentosRepetidosDescartados()).isEqualTo(1);
    }

    @Test
    void deveRecusarContagemNegativaDeCopias() {
        assertThatThrownBy(() -> new LoteDeDocumentos(HASH_DA_ENTRADA, List.of(), -1))
                .isInstanceOf(AuditoriaInvalida.class);
    }

    private static DocumentoComItens documentoComUmItem() {
        Documento documento = CenarioFicticio.documento();
        ItemDocumento item = ConstrutorDeItem.item().numero(1).construir();
        return new DocumentoComItens(documento, List.of(item));
    }
}
