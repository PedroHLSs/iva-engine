package br.edu.tcc.auditoria.aplicacao.auditoria;

import br.edu.tcc.auditoria.dominio.Documento;
import br.edu.tcc.auditoria.dominio.ItemDocumento;
import br.edu.tcc.auditoria.dominio.ResultadoAvaliacao;
import br.edu.tcc.auditoria.dominio.catalogo.ContextoNormativo;
import br.edu.tcc.auditoria.dominio.regras.Avaliacao;
import br.edu.tcc.auditoria.dominio.regras.CenarioFicticio;
import br.edu.tcc.auditoria.dominio.regras.ConjuntoRegras;
import br.edu.tcc.auditoria.dominio.regras.ConstrutorDeItem;
import br.edu.tcc.auditoria.dominio.regras.ContextoNormativoFalso;
import br.edu.tcc.auditoria.dominio.regras.ToleranciaDeValor;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** O motor: percorre documentos, itens e regras, e devolve tudo em ordem fixa. */
class MotorAuditoriaTest {

    private static final MotorAuditoria MOTOR = new MotorAuditoria();

    private static final ConjuntoRegras CONJUNTO =
            ConjuntoRegras.padrao(CenarioFicticio.coberturaTotal(), ToleranciaDeValor.exata());

    @Test
    void deveProduzirAMesmaListaCampoACampoEmDuasExecucoes() {
        // Requisito da etapa: mesmo conjunto de entrada, mesma lista, sempre. A
        // comparação é recursiva, campo a campo, e não por identidade de objeto:
        // duas execuções constroem avaliações novas, e o que precisa coincidir é
        // o conteúdo — inclusive evidências, fundamentos e valores em risco.
        List<DocumentoComItens> documentos = loteFicticio();

        List<Avaliacao> primeira = MOTOR.auditar(documentos, provedor(), CONJUNTO);
        List<Avaliacao> segunda = MOTOR.auditar(documentos, provedor(), CONJUNTO);

        assertThat(primeira).usingRecursiveComparison().isEqualTo(segunda);
    }

    @Test
    void deveOrdenarPorDocumentoDepoisItemDepoisRegra() {
        List<Avaliacao> avaliacoes = MOTOR.auditar(loteFicticio(), provedor(), CONJUNTO);

        List<String> enderecos = avaliacoes.stream()
                .map(avaliacao -> "%s/%d/%s".formatted(
                        avaliacao.chaveAcesso().valor(),
                        avaliacao.numeroItem().orElseThrow(),
                        avaliacao.regraId()))
                .toList();

        assertThat(enderecos).isSorted();
        assertThat(enderecos).startsWith(
                "%s/1/R01".formatted(CenarioFicticio.CHAVE_PRIMEIRA),
                "%s/1/R02".formatted(CenarioFicticio.CHAVE_PRIMEIRA));
    }

    @Test
    void deveIndependerDaOrdemEmQueOsDocumentosChegaram() {
        // A ordem de entrada costuma vir de listagem de diretório ou de consulta
        // a banco, e nenhuma das duas promete estabilidade. Se a saída seguisse a
        // entrada, comparar dois relatórios do mesmo lote acusaria diferença onde
        // não há.
        List<DocumentoComItens> naOrdem = loteFicticio();
        List<DocumentoComItens> invertido = new ArrayList<>(naOrdem);
        java.util.Collections.reverse(invertido);

        assertThat(MOTOR.auditar(naOrdem, provedor(), CONJUNTO))
                .usingRecursiveComparison()
                .isEqualTo(MOTOR.auditar(invertido, provedor(), CONJUNTO));
    }

    @Test
    void deveAplicarTodasAsRegrasATodosOsItens() {
        List<Avaliacao> avaliacoes = MOTOR.auditar(loteFicticio(), provedor(), CONJUNTO);

        // dois documentos, dois itens cada, sete regras
        assertThat(avaliacoes).hasSize(2 * 2 * 7);
    }

    @Test
    void deveManterNaSaidaAsAvaliacoesConformesENaoAvaliadas() {
        // Filtrar aqui faria o relatório perder a informação de quantas regras
        // não puderam ser aplicadas — que é o que distingue auditoria honesta de
        // auditoria que parece limpa.
        List<Avaliacao> avaliacoes = MOTOR.auditar(loteFicticio(), provedor(), CONJUNTO);

        assertThat(avaliacoes).extracting(Avaliacao::resultado)
                .contains(ResultadoAvaliacao.NAO_AVALIADO)
                .contains(ResultadoAvaliacao.CONFORME);
    }

    @Test
    void deveConstruirUmContextoPorDocumentoAuditado() {
        // Documentos de datas diferentes não podem compartilhar catálogo
        // resolvido; a contagem prova que o motor pede um contexto por documento.
        List<Documento> pedidos = new ArrayList<>();
        ProvedorDeContextoNormativo provedorQueRegistra = documento -> {
            pedidos.add(documento);
            return ContextoNormativoFalso.vazio();
        };

        MOTOR.auditar(loteFicticio(), provedorQueRegistra, CONJUNTO);

        assertThat(pedidos).hasSize(2);
        assertThat(pedidos).extracting(documento -> documento.chaveAcesso().valor())
                .containsExactly(CenarioFicticio.CHAVE_PRIMEIRA, CenarioFicticio.CHAVE_SEGUNDA);
    }

    @Test
    void deveOrdenarOsItensPorNumeroAindaQueTenhamChegadoTrocados() {
        DocumentoComItens documento = new DocumentoComItens(
                CenarioFicticio.documento(),
                List.of(item(2), item(1)));

        List<Avaliacao> avaliacoes = MOTOR.auditar(documento, ContextoNormativoFalso.vazio(), CONJUNTO);

        assertThat(avaliacoes).extracting(avaliacao -> avaliacao.numeroItem().orElseThrow())
                .startsWith(1, 1, 1, 1, 1, 1, 1)
                .endsWith(2, 2, 2, 2, 2, 2, 2);
    }

    @Test
    void deveRecusarChamadaSemOsInsumos() {
        DocumentoComItens documento = new DocumentoComItens(CenarioFicticio.documento(), List.of(item(1)));

        assertThatThrownBy(() -> MOTOR.auditar(documento, null, CONJUNTO))
                .isInstanceOf(AuditoriaInvalida.class);
        assertThatThrownBy(() -> MOTOR.auditar(documento, ContextoNormativoFalso.vazio(), null))
                .isInstanceOf(AuditoriaInvalida.class);
        assertThatThrownBy(() -> MOTOR.auditar(loteFicticio(), documentoQualquer -> null, CONJUNTO))
                .isInstanceOf(AuditoriaInvalida.class);
    }

    @Test
    void deveAceitarDocumentoSemItensSemProduzirAvaliacao() {
        DocumentoComItens semItens = new DocumentoComItens(CenarioFicticio.documento(), List.of());

        assertThat(MOTOR.auditar(semItens, ContextoNormativoFalso.vazio(), CONJUNTO)).isEmpty();
    }

    /**
     * Lote com dois documentos de dois itens cada, entregue fora de ordem de
     * chave de propósito.
     */
    private static List<DocumentoComItens> loteFicticio() {
        return List.of(
                new DocumentoComItens(
                        CenarioFicticio.documento(CenarioFicticio.CHAVE_SEGUNDA, CenarioFicticio.DATA_EMISSAO),
                        List.of(item(1), item(2))),
                new DocumentoComItens(
                        CenarioFicticio.documento(CenarioFicticio.CHAVE_PRIMEIRA, CenarioFicticio.DATA_EMISSAO),
                        List.of(item(1), item(2))));
    }

    /**
     * Contexto com catálogo suficiente para haver conformes, achados e não
     * avaliados na mesma execução.
     */
    private static ProvedorDeContextoNormativo provedor() {
        return documento -> catalogo();
    }

    private static ContextoNormativo catalogo() {
        return ContextoNormativoFalso.vazio()
                .com(CenarioFicticio.classificacao(CenarioFicticio.CODIGO, CenarioFicticio.CST))
                .com(CenarioFicticio.registroNcm(CenarioFicticio.NCM));
    }

    private static ItemDocumento item(int numero) {
        return ConstrutorDeItem.item()
                .numero(numero)
                .ncm(CenarioFicticio.NCM)
                .classificacao(CenarioFicticio.CODIGO)
                .cstIbs(CenarioFicticio.CST)
                .construir();
    }
}
