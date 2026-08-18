package br.edu.tcc.auditoria.dominio.regras;

import br.edu.tcc.auditoria.dominio.ItemDocumento;
import org.junit.jupiter.api.Test;

import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** O vocabulário pelo qual o catálogo se refere aos campos de um item. */
class CampoDoItemTest {

    @Test
    void todoNomeDoVocabularioDeveCorresponderAUmCampoDeItemDocumento() {
        // Se um campo do item for renomeado, este teste falha. Sem ele, R07
        // passaria a ignorar em silêncio uma exigência do catálogo, e a
        // exigência não conferida sairia do relatório sem aviso.
        List<String> camposDoItem = Arrays.stream(ItemDocumento.class.getRecordComponents())
                .map(RecordComponent::getName)
                .toList();

        assertThat(Arrays.stream(CampoDoItem.values()).map(CampoDoItem::nomeNoCatalogo).toList())
                .allSatisfy(nome -> assertThat(camposDoItem).contains(nome));
    }

    @Test
    void naoDeveReconhecerNomeForaDoVocabulario() {
        assertThat(CampoDoItem.porNome("campoInexistenteXX")).isEmpty();
        assertThat(CampoDoItem.porNome(null)).isEmpty();
    }

    @Test
    void naoDeveReconhecerNomeComCaixaDiferente() {
        // Normalizar texto lido do CSV é trabalho da importação, não do domínio.
        assertThat(CampoDoItem.porNome("BASECALCULOIBS")).isEmpty();
    }

    @Test
    void deveConsiderarPreenchidoOCampoDeclaradoComoZero() {
        ItemDocumento zerado = ConstrutorDeItem.item().baseCalculoIbs("0.00").construir();
        ItemDocumento ausente = ConstrutorDeItem.item().construir();

        assertThat(CampoDoItem.BASE_CALCULO_IBS.estaPreenchidoEm(zerado)).isTrue();
        assertThat(CampoDoItem.BASE_CALCULO_IBS.estaPreenchidoEm(ausente)).isFalse();
    }
}
