package br.edu.tcc.auditoria.infraestrutura.api;

import br.edu.tcc.auditoria.infraestrutura.upload.LimitesDeUpload;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

/**
 * Configuração própria da API.
 *
 * <p>Arquivo separado de {@code ConfiguracaoDaAuditoria}, da Etapa 5, de
 * propósito: a Etapa 8 não altera nada do que já estava entregue, e a API não
 * precisa que ninguém mexa naquele arquivo para existir. Os objetos das camadas
 * de dentro que ela usa já são beans lá.</p>
 */
@Configuration
class ConfiguracaoDaApi {

    static final String PROPRIEDADE_EXPOR_CHAVE = "auditoria.api.expor-chave-de-acesso";

    static final String PROPRIEDADE_EXPOR_JUSTIFICATIVA = "auditoria.api.expor-justificativa";

    /*
     * Acrescentada na etapa de conferência.
     *
     * A descrição do produto é o segundo campo de texto livre do sistema, e o
     * primeiro que vem da fonte em escala e sem revisão. Mesmo regime dos
     * outros dois: desligada por padrão, com o motivo escrito no lugar.
     */
    static final String PROPRIEDADE_EXPOR_DESCRICAO =
            "auditoria.api.expor-descricao-do-produto";

    /**
     * A política de exposição, desligada em tudo por padrão.
     *
     * <p>O padrão é restritivo <em>na ausência da propriedade</em>, e não só
     * quando ela diz {@code false}. É o inverso do que a Etapa 5 fez com a
     * tolerância de valor e o sal, que não têm padrão nenhum e param o sistema:
     * ali a falta de escolha é ambígua, aqui não é. Não configurar quer dizer "não
     * exponha", que é a leitura segura — e a única em que esquecer de configurar
     * não vaza nada.</p>
     */
    /**
     * Limites do que a API aceita receber.
     *
     * <p>Acrescentado nesta etapa, quando a API deixou de ser só de leitura.
     * Ao contrário do sal e da tolerância, <strong>têm padrão</strong>: um teto
     * de tamanho de arquivo não afirma nada sobre documento nenhum, e parar o
     * sistema na subida por falta dele seria zelo mal colocado. Ver
     * {@link LimitesDeUpload}.</p>
     */
    @Bean
    LimitesDeUpload limitesDeUpload(Environment ambiente) {
        LimitesDeUpload padrao = LimitesDeUpload.padrao();
        return new LimitesDeUpload(
                bytes(ambiente, "auditoria.upload.tamanho-maximo", padrao.tamanhoMaximoDoEnvio()),
                (int) bytes(ambiente, "auditoria.upload.entradas-maximas",
                        padrao.entradasMaximasNoPacote()),
                bytes(ambiente, "auditoria.upload.tamanho-maximo-por-entrada",
                        padrao.tamanhoMaximoPorEntrada()),
                bytes(ambiente, "auditoria.upload.total-descomprimido-maximo",
                        padrao.totalDescomprimidoMaximo()),
                bytes(ambiente, "auditoria.upload.razao-de-compressao-maxima",
                        padrao.razaoDeCompressaoMaxima()),
                bytes(ambiente, "auditoria.upload.piso-para-conferir-razao",
                        padrao.pisoParaConferirRazao()));
    }

    private static long bytes(Environment ambiente, String propriedade, long padrao) {
        return ambiente.getProperty(propriedade, Long.class, padrao);
    }

    @Bean
    PoliticaDeExposicao politicaDeExposicao(Environment ambiente) {
        return new PoliticaDeExposicao(
                ambiente.getProperty(PROPRIEDADE_EXPOR_CHAVE, Boolean.class, false),
                ambiente.getProperty(PROPRIEDADE_EXPOR_JUSTIFICATIVA, Boolean.class, false),
                ambiente.getProperty(PROPRIEDADE_EXPOR_DESCRICAO, Boolean.class, false));
    }
}
