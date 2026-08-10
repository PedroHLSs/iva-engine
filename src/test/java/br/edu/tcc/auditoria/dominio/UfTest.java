package br.edu.tcc.auditoria.dominio;

import br.edu.tcc.auditoria.dominio.excecao.UfInvalida;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UfTest {

    @Test
    void deveConterAsVinteESeteUnidadesFederativas() {
        assertThat(Uf.values()).hasSize(27);
    }

    @Test
    void deveConverterSiglaConhecida() {
        assertThat(Uf.de("SP")).isEqualTo(Uf.SP);
    }

    @Test
    void deveExporASiglaDeDuasLetras() {
        assertThat(Uf.MG.sigla()).isEqualTo("MG");
    }

    @Test
    void deveRejeitarSiglaNula() {
        assertThatThrownBy(() -> Uf.de(null)).isInstanceOf(UfInvalida.class);
    }

    @Test
    void deveRejeitarSiglaInexistente() {
        assertThatThrownBy(() -> Uf.de("XX")).isInstanceOf(UfInvalida.class);
    }

    @Test
    void deveRejeitarSiglaEmMinusculas() {
        // Normalizar o texto lido do XML é tarefa da infraestrutura, não do domínio.
        assertThatThrownBy(() -> Uf.de("sp")).isInstanceOf(UfInvalida.class);
    }

    @Test
    void deveRejeitarSiglaComEspacoEmVolta() {
        assertThatThrownBy(() -> Uf.de(" SP")).isInstanceOf(UfInvalida.class);
    }

    @Test
    void deveRejeitarSiglaVazia() {
        assertThatThrownBy(() -> Uf.de("")).isInstanceOf(UfInvalida.class);
    }
}
