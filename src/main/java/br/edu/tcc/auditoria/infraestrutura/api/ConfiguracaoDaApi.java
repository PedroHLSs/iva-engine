package br.edu.tcc.auditoria.infraestrutura.api;

import br.edu.tcc.auditoria.infraestrutura.upload.LimitesDeUpload;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

// Classe de configuração do Spring só da API, separada de ConfiguracaoDaAuditoria para a API existir sem mexer naquele arquivo.
@Configuration
class ConfiguracaoDaApi {

    // Nomes das propriedades que ligam a chave de acesso e a justificativa da tratativa nas respostas.
    static final String PROPRIEDADE_EXPOR_CHAVE = "auditoria.api.expor-chave-de-acesso";

    static final String PROPRIEDADE_EXPOR_JUSTIFICATIVA = "auditoria.api.expor-justificativa";

    // Propriedade que liga a descrição do produto, texto livre vindo do emitente; também vem desligada por padrão.
    static final String PROPRIEDADE_EXPOR_DESCRICAO =
            "auditoria.api.expor-descricao-do-produto";

    // Cria os limites do que a API aceita receber no envio de arquivos. Aqui há valor padrão, porque um limite de tamanho não afirma nada sobre documento nenhum.
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

    // Método auxiliar que lê uma propriedade numérica, ou usa o padrão quando ela não foi configurada.
    private static long bytes(Environment ambiente, String propriedade, long padrao) {
        return ambiente.getProperty(propriedade, Long.class, padrao);
    }

    // Cria a política de exposição, com tudo desligado quando a propriedade não foi configurada: esquecer de configurar não vaza nada.
    @Bean
    PoliticaDeExposicao politicaDeExposicao(Environment ambiente) {
        return new PoliticaDeExposicao(
                ambiente.getProperty(PROPRIEDADE_EXPOR_CHAVE, Boolean.class, false),
                ambiente.getProperty(PROPRIEDADE_EXPOR_JUSTIFICATIVA, Boolean.class, false),
                ambiente.getProperty(PROPRIEDADE_EXPOR_DESCRICAO, Boolean.class, false));
    }
}
