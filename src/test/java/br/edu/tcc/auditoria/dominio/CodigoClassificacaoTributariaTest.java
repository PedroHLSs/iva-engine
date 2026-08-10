package br.edu.tcc.auditoria.dominio;

import br.edu.tcc.auditoria.dominio.excecao.CodigoClassificacaoTributariaInvalido;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CodigoClassificacaoTributariaTest {

    @Test
    void deveAceitarQualquerCodigoNaoVazio() {
        assertThat(new CodigoClassificacaoTributaria(DadosFicticios.CLASSIFICACAO_TRIBUTARIA).valor())
                .isEqualTo(DadosFicticios.CLASSIFICACAO_TRIBUTARIA);
    }

    @Test
    void naoDeveRestringirOConjuntoDeCodigosAceitos() {
        // A tabela de cClassTrib é normativa e chega por importação.
        assertThat(new CodigoClassificacaoTributaria("A").valor()).isEqualTo("A");
        assertThat(new CodigoClassificacaoTributaria("99999999").valor()).isEqualTo("99999999");
    }

    @Test
    void deveRejeitarCodigoNulo() {
        assertThatThrownBy(() -> new CodigoClassificacaoTributaria(null))
                .isInstanceOf(CodigoClassificacaoTributariaInvalido.class);
    }

    @Test
    void deveRejeitarCodigoVazio() {
        assertThatThrownBy(() -> new CodigoClassificacaoTributaria(""))
                .isInstanceOf(CodigoClassificacaoTributariaInvalido.class);
    }

    @Test
    void deveRejeitarCodigoComEspacoInterno() {
        assertThatThrownBy(() -> new CodigoClassificacaoTributaria("99 99"))
                .isInstanceOf(CodigoClassificacaoTributariaInvalido.class);
    }

    @Test
    void deveSerTipoDistintoDeCodigoCst() {
        // Não são intercambiáveis: trocá-los é erro que o compilador precisa pegar.
        assertThat(new CodigoClassificacaoTributaria("999"))
                .isNotEqualTo(new CodigoCst("999"));
    }
}
