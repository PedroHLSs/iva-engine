package br.edu.tcc.auditoria.dominio;

import br.edu.tcc.auditoria.dominio.excecao.CodigoCstInvalido;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CodigoCstTest {

    @Test
    void deveAceitarQualquerCodigoNaoVazio() {
        assertThat(new CodigoCst(DadosFicticios.CST).valor()).isEqualTo(DadosFicticios.CST);
    }

    @Test
    void naoDeveRestringirOConjuntoDeCodigosAceitos() {
        // O conjunto de códigos válidos é normativo e chega por importação de CSV.
        // O construtor não pode conhecê-lo, sob pena de o código afirmar legislação.
        assertThat(new CodigoCst("ZZZ").valor()).isEqualTo("ZZZ");
        assertThat(new CodigoCst("9").valor()).isEqualTo("9");
        assertThat(new CodigoCst("9999999999").valor()).isEqualTo("9999999999");
    }

    @Test
    void deveRejeitarCodigoNulo() {
        assertThatThrownBy(() -> new CodigoCst(null)).isInstanceOf(CodigoCstInvalido.class);
    }

    @Test
    void deveRejeitarCodigoVazio() {
        assertThatThrownBy(() -> new CodigoCst("")).isInstanceOf(CodigoCstInvalido.class);
    }

    @Test
    void deveRejeitarCodigoSoComEspacos() {
        assertThatThrownBy(() -> new CodigoCst("   ")).isInstanceOf(CodigoCstInvalido.class);
    }

    @Test
    void deveRejeitarCodigoComEspacoInterno() {
        assertThatThrownBy(() -> new CodigoCst("A A")).isInstanceOf(CodigoCstInvalido.class);
    }
}
