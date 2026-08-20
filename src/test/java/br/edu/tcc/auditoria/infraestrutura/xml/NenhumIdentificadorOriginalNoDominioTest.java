package br.edu.tcc.auditoria.infraestrutura.xml;

import br.edu.tcc.auditoria.aplicacao.auditoria.DocumentoComItens;
import br.edu.tcc.auditoria.dominio.ChaveAcesso;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.RecordComponent;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guarda automatizada da promessa central da normalização: <strong>CNPJ e CPF
 * não entram no domínio</strong>.
 *
 * <p>A verificação não confia na leitura do código. Ela varre o objeto
 * normalizado inteiro por reflexão — todo componente de todo {@code record},
 * dentro de {@code Optional} e dentro de lista — junta tudo que é texto e
 * procura pelos identificadores que estavam no XML de entrada. Se algum
 * aparecer, em qualquer campo, o teste falha.</p>
 *
 * <p>A varredura cobre inclusive campos que hoje não têm como guardar
 * identificador: o valor do teste está justamente em pegar o campo que alguém
 * acrescentar amanhã.</p>
 *
 * <h2>O que a varredura deixa de fora, e por quê</h2>
 *
 * <p>{@link ChaveAcesso} é ignorada. A chave de acesso de um documento real
 * carrega o CNPJ do emitente nas suas posições intermediárias, por definição do
 * leiaute, e o sistema precisa dela inteira: é a identidade do documento
 * auditado, é o que liga o apontamento ao arquivo de origem e é o que o
 * contribuinte usa para conferir. Pseudonimizar a chave tornaria o relatório
 * inútil; recortá-la a tornaria ambígua.</p>
 *
 * <p><strong>Documento com participantes pseudonimizados não é documento
 * anônimo.</strong> A pseudonimização impede que CNPJ e CPF circulem como campo
 * consultável do domínio; ela não anonimiza o acervo. Ver D005 em
 * {@code docs/DECISOES-ARQUITETURA.md}.</p>
 */
class NenhumIdentificadorOriginalNoDominioTest {

    private final LeitorDocumentoFiscal leitor = DocumentoDeTeste.leitor();
    private final NormalizadorDocumento normalizador = DocumentoDeTeste.normalizador();

    @Test
    void naoDeveGuardarOCnpjDoEmitenteEmLugarNenhumDoDocumentoNormalizado() throws IOException {
        List<String> textos = textosDe(normalizar(DocumentoDeTeste.ITEM_COMPLETO));

        assertThat(textos)
                .as("o CNPJ do emitente entrou no XML e não pode existir em nenhum campo do domínio")
                .noneMatch(texto -> texto.contains(DocumentoDeTeste.CNPJ_DO_EMITENTE));
    }

    @Test
    void naoDeveGuardarOCnpjDoDestinatarioEmLugarNenhumDoDocumentoNormalizado() throws IOException {
        List<String> textos = textosDe(normalizar(DocumentoDeTeste.ITEM_COMPLETO));

        assertThat(textos).noneMatch(texto -> texto.contains(DocumentoDeTeste.CNPJ_DO_DESTINATARIO));
    }

    @Test
    void naoDeveGuardarOCpfDoDestinatarioEmLugarNenhumDoDocumentoNormalizado() throws IOException {
        List<String> textos = textosDe(normalizar(DocumentoDeTeste.MULTIPLOS_ITENS));

        assertThat(textos).noneMatch(texto -> texto.contains(DocumentoDeTeste.CPF_DO_DESTINATARIO));
    }

    @Test
    void naoDeveExporIdentificadorNemQuandoODocumentoInteiroVirarTexto() throws IOException {
        String documentoEmTexto = normalizar(DocumentoDeTeste.ITEM_COMPLETO).toString();

        assertThat(documentoEmTexto)
                .as("registro de log e mensagem de erro imprimem o objeto inteiro")
                .doesNotContain(DocumentoDeTeste.CNPJ_DO_EMITENTE)
                .doesNotContain(DocumentoDeTeste.CNPJ_DO_DESTINATARIO);
    }

    @Test
    void deveEncontrarTextoParaVasculhar() throws IOException {
        assertThat(textosDe(normalizar(DocumentoDeTeste.ITEM_COMPLETO)))
                .as("uma varredura que não enxerga campo nenhum passaria sempre")
                .hasSizeGreaterThan(10);
    }

    private DocumentoComItens normalizar(String nomeDoDocumento) throws IOException {
        try (InputStream conteudo = DocumentoDeTeste.abrir(nomeDoDocumento)) {
            return normalizador.normalizar(leitor.ler(conteudo));
        }
    }

    /** Todo texto alcançável a partir do objeto, atravessando records, Optional e listas. */
    private static List<String> textosDe(Object objeto) {
        List<String> textos = new ArrayList<>();
        recolher(objeto, textos);
        return textos;
    }

    private static void recolher(Object valor, List<String> textos) {
        switch (valor) {
            case null -> {
            }
            case ChaveAcesso ignorada -> {
                // Ver a explicação no cabeçalho desta classe.
            }
            case String texto -> textos.add(texto);
            case Optional<?> talvez -> talvez.ifPresent(presente -> recolher(presente, textos));
            case Collection<?> colecao -> colecao.forEach(elemento -> recolher(elemento, textos));
            default -> {
                if (valor.getClass().isRecord()) {
                    recolherComponentes(valor, textos);
                } else {
                    textos.add(String.valueOf(valor));
                }
            }
        }
    }

    private static void recolherComponentes(Object registro, List<String> textos) {
        for (RecordComponent componente : registro.getClass().getRecordComponents()) {
            try {
                recolher(componente.getAccessor().invoke(registro), textos);
            } catch (IllegalAccessException | InvocationTargetException erro) {
                throw new IllegalStateException(
                        "A varredura não conseguiu ler o componente %s de %s."
                                .formatted(componente.getName(), registro.getClass().getSimpleName()), erro);
            }
        }
    }
}
