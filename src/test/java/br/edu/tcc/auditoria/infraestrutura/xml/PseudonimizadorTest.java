package br.edu.tcc.auditoria.infraestrutura.xml;

import br.edu.tcc.auditoria.dominio.IdentificadorPseudonimizado;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PseudonimizadorTest {

    private static final String OUTRO_SAL = "outro-sal-ficticio-de-teste-bbbbbbbbbbbb";

    private final Pseudonimizador pseudonimizador = DocumentoDeTeste.pseudonimizador();

    @Test
    void devePseudonimizarEmHexadecimalMinusculoDeSessentaEQuatroCaracteres() {
        IdentificadorPseudonimizado pseudonimo =
                pseudonimizador.pseudonimizar(DocumentoDeTeste.CNPJ_DO_EMITENTE);

        assertThat(pseudonimo.valor()).hasSize(64).matches("[0-9a-f]{64}");
    }

    @Test
    void deveProduzirOMesmoPseudonimoParaOMesmoIdentificador() {
        assertThat(pseudonimizador.pseudonimizar(DocumentoDeTeste.CNPJ_DO_EMITENTE))
                .as("o pseudônimo precisa ser estável, senão não dá para reconhecer o mesmo participante")
                .isEqualTo(pseudonimizador.pseudonimizar(DocumentoDeTeste.CNPJ_DO_EMITENTE));
    }

    @Test
    void deveProduzirPseudonimosDiferentesParaIdentificadoresDiferentes() {
        assertThat(pseudonimizador.pseudonimizar(DocumentoDeTeste.CNPJ_DO_EMITENTE))
                .isNotEqualTo(pseudonimizador.pseudonimizar(DocumentoDeTeste.CNPJ_DO_DESTINATARIO));
    }

    @Test
    void deveProduzirPseudonimosDiferentesParaSaisDiferentes() {
        Pseudonimizador deOutraInstalacao = new Pseudonimizador(new SalDeInstalacao(OUTRO_SAL));

        assertThat(pseudonimizador.pseudonimizar(DocumentoDeTeste.CNPJ_DO_EMITENTE))
                .as("o mesmo CNPJ em instalações diferentes não pode produzir o mesmo pseudônimo")
                .isNotEqualTo(deOutraInstalacao.pseudonimizar(DocumentoDeTeste.CNPJ_DO_EMITENTE));
    }

    @Test
    void deveIgnorarEspacoEmVoltaDoIdentificador() {
        assertThat(pseudonimizador.pseudonimizar("  " + DocumentoDeTeste.CNPJ_DO_EMITENTE + "\n"))
                .isEqualTo(pseudonimizador.pseudonimizar(DocumentoDeTeste.CNPJ_DO_EMITENTE));
    }

    @Test
    void deveRecusarIdentificadorAusente() {
        assertThatThrownBy(() -> pseudonimizador.pseudonimizar("   "))
                .isInstanceOf(DocumentoFiscalIlegivel.class)
                .hasMessageContaining("Optional.empty()");
    }

    @Test
    void deveExigirSalNaConstrucao() {
        assertThatThrownBy(() -> new Pseudonimizador(null))
                .isInstanceOf(SalDeInstalacaoInvalido.class);
    }
}
