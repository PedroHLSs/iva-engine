package br.edu.tcc.auditoria.dominio;

import br.edu.tcc.auditoria.dominio.excecao.NcmInvalido;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NcmTest {

    @Test
    void deveAceitarNcmComOitoDigitos() {
        assertThat(new Ncm(DadosFicticios.NCM).valor()).isEqualTo(DadosFicticios.NCM);
    }

    @Test
    void deveRejeitarNcmNulo() {
        assertThatThrownBy(() -> new Ncm(null)).isInstanceOf(NcmInvalido.class);
    }

    @Test
    void deveRejeitarNcmVazio() {
        assertThatThrownBy(() -> new Ncm("")).isInstanceOf(NcmInvalido.class);
    }

    @Test
    void deveRejeitarNcmComMenosDeOitoDigitos() {
        assertThatThrownBy(() -> new Ncm("0000000")).isInstanceOf(NcmInvalido.class);
    }

    @Test
    void deveRejeitarNcmComMaisDeOitoDigitos() {
        assertThatThrownBy(() -> new Ncm("000000000")).isInstanceOf(NcmInvalido.class);
    }

    @Test
    void deveRejeitarNcmComLetra() {
        assertThatThrownBy(() -> new Ncm("0000000A")).isInstanceOf(NcmInvalido.class);
    }

    @Test
    void deveRejeitarNcmComPonto() {
        assertThatThrownBy(() -> new Ncm("0000.000")).isInstanceOf(NcmInvalido.class);
    }
}
