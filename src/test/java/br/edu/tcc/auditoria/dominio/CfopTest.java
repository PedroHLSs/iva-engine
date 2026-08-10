package br.edu.tcc.auditoria.dominio;

import br.edu.tcc.auditoria.dominio.excecao.CfopInvalido;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CfopTest {

    @Test
    void deveAceitarCfopComQuatroDigitos() {
        assertThat(new Cfop(DadosFicticios.CFOP).valor()).isEqualTo(DadosFicticios.CFOP);
    }

    @Test
    void deveRejeitarCfopNulo() {
        assertThatThrownBy(() -> new Cfop(null)).isInstanceOf(CfopInvalido.class);
    }

    @Test
    void deveRejeitarCfopVazio() {
        assertThatThrownBy(() -> new Cfop("")).isInstanceOf(CfopInvalido.class);
    }

    @Test
    void deveRejeitarCfopComMenosDeQuatroDigitos() {
        assertThatThrownBy(() -> new Cfop("999")).isInstanceOf(CfopInvalido.class);
    }

    @Test
    void deveRejeitarCfopComMaisDeQuatroDigitos() {
        assertThatThrownBy(() -> new Cfop("99999")).isInstanceOf(CfopInvalido.class);
    }

    @Test
    void deveRejeitarCfopComSeparador() {
        assertThatThrownBy(() -> new Cfop("9.99")).isInstanceOf(CfopInvalido.class);
    }
}
