package br.edu.tcc.auditoria.infraestrutura.acuracia;

import br.edu.tcc.auditoria.aplicacao.acuracia.Gabarito;
import br.edu.tcc.auditoria.aplicacao.acuracia.LinhaDeGabarito;
import br.edu.tcc.auditoria.dominio.ChaveAcesso;
import br.edu.tcc.auditoria.dominio.acuracia.RotuloEsperado;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.StringReader;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Leitura do gabarito.
 *
 * <p><strong>Todos os valores são fictícios.</strong> Chaves de um dígito
 * repetido quarenta e quatro vezes e identificadores de regra "RX1" não existem
 * em acervo nenhum.</p>
 */
class LeitorDeGabaritoCsvTest {

    private static final String CHAVE = "1".repeat(44);
    private static final String OUTRA_CHAVE = "2".repeat(44);

    private final LeitorDeGabaritoCsv leitor = new LeitorDeGabaritoCsv();

    @Test
    void deveLerAsQuatroColunasDeCadaLinha() throws IOException {
        Gabarito gabarito = ler("""
                # gabarito fictício de teste
                chave_documento;numero_item;regra_id;rotulo_esperado
                %s;1;RX1;ACHADO
                %s;2;RX1;CONFORME
                """.formatted(CHAVE, CHAVE));

        assertThat(gabarito.quantidadeDeLinhas()).isEqualTo(2);

        LinhaDeGabarito primeira = gabarito.linhas().get(0);
        assertThat(primeira.endereco().chaveAcesso()).isEqualTo(new ChaveAcesso(CHAVE));
        assertThat(primeira.endereco().numeroItem()).isEqualTo(1);
        assertThat(primeira.regraId()).isEqualTo("RX1");
        assertThat(primeira.rotulo()).isEqualTo(RotuloEsperado.ACHADO);
        assertThat(gabarito.linhas().get(1).rotulo()).isEqualTo(RotuloEsperado.CONFORME);
    }

    @Test
    void deveGuardarONumeroDaLinhaFisicaContandoComentarios() throws IOException {
        Gabarito gabarito = ler("""
                # comentário de topo

                chave_documento;numero_item;regra_id;rotulo_esperado
                %s;1;RX1;ACHADO
                # comentário no meio
                %s;1;RX1;CONFORME
                """.formatted(CHAVE, OUTRA_CHAVE));

        assertThat(gabarito.linhas().get(0).numeroDaLinha()).isEqualTo(4);
        assertThat(gabarito.linhas().get(1).numeroDaLinha()).isEqualTo(6);
    }

    @Test
    void deveAceitarAsColunasEmQualquerOrdem() throws IOException {
        Gabarito gabarito = ler("""
                rotulo_esperado;regra_id;numero_item;chave_documento
                ACHADO;RX1;7;%s
                """.formatted(CHAVE));

        assertThat(gabarito.linhas().get(0).endereco().numeroItem()).isEqualTo(7);
        assertThat(gabarito.linhas().get(0).regraId()).isEqualTo("RX1");
    }

    @Test
    void rotuloEmBrancoNaoPodeViraConformePorOmissao() {
        assertThatThrownBy(() -> ler("""
                chave_documento;numero_item;regra_id;rotulo_esperado
                %s;1;RX1;
                """.formatted(CHAVE)))
                .isInstanceOf(GabaritoInvalido.class)
                .hasMessageContaining("Linha 2")
                .hasMessageContaining("rotulo_esperado");
    }

    @Test
    void deveRecusarRotuloDesconhecidoIndicandoALinha() {
        assertThatThrownBy(() -> ler("""
                chave_documento;numero_item;regra_id;rotulo_esperado
                %s;1;RX1;TALVEZ
                """.formatted(CHAVE)))
                .isInstanceOf(GabaritoInvalido.class)
                .hasMessageContaining("Linha 2")
                .hasMessageContaining("TALVEZ");
    }

    @Test
    void deveRecusarNaoAvaliadoComoRotuloDeGabarito() {
        assertThatThrownBy(() -> ler("""
                chave_documento;numero_item;regra_id;rotulo_esperado
                %s;1;RX1;NAO_AVALIADO
                """.formatted(CHAVE)))
                .isInstanceOf(GabaritoInvalido.class)
                .hasMessageContaining("desfecho do sistema");
    }

    @Test
    void deveRecusarChaveDeAcessoMalformadaIndicandoALinha() {
        assertThatThrownBy(() -> ler("""
                chave_documento;numero_item;regra_id;rotulo_esperado
                123;1;RX1;ACHADO
                """))
                .isInstanceOf(GabaritoInvalido.class)
                .hasMessageContaining("Linha 2")
                .hasMessageContaining("44");
    }

    @Test
    void deveRecusarNumeroDeItemMenorQueUm() {
        assertThatThrownBy(() -> ler("""
                chave_documento;numero_item;regra_id;rotulo_esperado
                %s;0;RX1;ACHADO
                """.formatted(CHAVE)))
                .isInstanceOf(GabaritoInvalido.class)
                .hasMessageContaining("Linha 2")
                .hasMessageContaining("a partir de 1");
    }

    @Test
    void deveRecusarNumeroDeItemQueNaoENumero() {
        assertThatThrownBy(() -> ler("""
                chave_documento;numero_item;regra_id;rotulo_esperado
                %s;primeiro;RX1;ACHADO
                """.formatted(CHAVE)))
                .isInstanceOf(GabaritoInvalido.class)
                .hasMessageContaining("número inteiro");
    }

    @Test
    void deveRecusarColunaQueFaltaNoCabecalho() {
        assertThatThrownBy(() -> ler("""
                chave_documento;numero_item;regra_id
                %s;1;RX1
                """.formatted(CHAVE)))
                .isInstanceOf(GabaritoInvalido.class)
                .hasMessageContaining("rotulo_esperado");
    }

    @Test
    void deveRecusarOMesmoEnderecoRotuladoDuasVezes() {
        assertThatThrownBy(() -> ler("""
                chave_documento;numero_item;regra_id;rotulo_esperado
                %s;1;RX1;ACHADO
                %s;1;RX1;CONFORME
                """.formatted(CHAVE, CHAVE)))
                .isInstanceOf(GabaritoInvalido.class)
                .hasMessageContaining("linhas 2 e 3");
    }

    @Test
    void deveRecusarArquivoQueNaoExiste() {
        assertThatThrownBy(() -> leitor.carregar(Path.of("gabarito-que-nao-existe.csv")))
                .isInstanceOf(GabaritoInvalido.class)
                .hasMessageContaining("rotulado à mão");
    }

    private Gabarito ler(String conteudo) throws IOException {
        return leitor.ler(new StringReader(conteudo));
    }
}
