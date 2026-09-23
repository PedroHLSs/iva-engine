package br.edu.tcc.auditoria.infraestrutura.api;

import br.edu.tcc.auditoria.aplicacao.conferencia.AliquotaDoCatalogo;
import br.edu.tcc.auditoria.aplicacao.conferencia.ClassificacaoDoCatalogo;
import br.edu.tcc.auditoria.aplicacao.conferencia.DescricaoDeNcm;
import br.edu.tcc.auditoria.aplicacao.conferencia.EnquadramentoDoNcm;
import br.edu.tcc.auditoria.aplicacao.conferencia.ReferenciaNormativa;
import br.edu.tcc.auditoria.aplicacao.conferencia.TratamentoDeTributo;
import br.edu.tcc.auditoria.aplicacao.conferencia.TratamentoIdentificado;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

// Representa o tratamento que a base normativa indica, como a tela recebe. Os três tributos (as duas parcelas do IBS e a CBS) vêm sempre, em blocos separados e sem total, porque somar as parcelas daria um percentual que nenhuma linha da carga declara; a versão do catálogo e a data vão junto.
public record TratamentoExposto(
        String versaoDoCatalogo,
        LocalDate dataDeReferencia,
        boolean algoFoiDeterminado,
        LeituraExposta<NcmExposto> descricaoDoNcm,
        LeituraExposta<EnquadramentoExposto> enquadramentos,
        LeituraExposta<ClassificacaoExposta> classificacao,
        List<TributoExposto> porTributo) {

    // Valida que haja versão do catálogo, data, os três blocos de leitura e um bloco por tributo.
    public TratamentoExposto {
        if (versaoDoCatalogo == null || versaoDoCatalogo.isBlank()) {
            throw new RespostaInvalida("O tratamento precisa dizer de qual carga ele foi lido.");
        }
        if (dataDeReferencia == null) {
            throw new RespostaInvalida("O tratamento precisa da data em que foi resolvido.");
        }
        if (descricaoDoNcm == null || enquadramentos == null || classificacao == null) {
            throw new RespostaInvalida(
                    "Cada bloco do tratamento precisa existir, ainda que carregando o motivo de estar "
                            + "vazio.");
        }
        if (porTributo == null || porTributo.isEmpty()) {
            throw new RespostaInvalida(
                    "O tratamento precisa trazer um bloco por tributo, inclusive os que a carga não "
                            + "alcança. Tributo omitido é lido como tributo que não incide.");
        }
        porTributo = List.copyOf(porTributo);
    }

    // Método estático que converte o tratamento da aplicação para a resposta.
    static TratamentoExposto de(TratamentoIdentificado tratamento) {
        return new TratamentoExposto(
                tratamento.versaoDoCatalogo(),
                tratamento.dataDeReferencia(),
                tratamento.algoFoiDeterminado(),
                LeituraExposta.de(tratamento.descricaoDoNcm(), NcmExposto::de),
                LeituraExposta.de(tratamento.enquadramentos(), EnquadramentoExposto::de),
                LeituraExposta.de(tratamento.classificacao(), ClassificacaoExposta::de),
                tratamento.porTributo().stream().map(TributoExposto::de).toList());
    }

    // Representa a vigência e a fonte, exatamente como a carga declarou.
    public record ReferenciaExposta(
            String fonteNormativa,
            LocalDate vigenciaInicio,
            LocalDate vigenciaFim,
            String motivoDaVigenciaSemFim) {

        // Motivo escrito quando a carga não declarou último dia de vigência.
        static final String VIGENCIA_ABERTA =
                "a carga não declarou último dia de vigência para este registro";

        // Valida que haja fonte e início, e que o fim venha com data ou com o motivo, nunca os dois.
        public ReferenciaExposta {
            if (fonteNormativa == null || fonteNormativa.isBlank()) {
                throw new RespostaInvalida(
                        "A referência precisa da fonte: sem ela a tela afirmaria tratamento sem dizer "
                                + "de onde tirou.");
            }
            if (vigenciaInicio == null) {
                throw new RespostaInvalida("A referência precisa do início da vigência.");
            }
            if ((vigenciaFim == null) == (motivoDaVigenciaSemFim == null)) {
                throw new RespostaInvalida(
                        "Ou há fim de vigência, ou há o motivo de não haver. Data em branco sem "
                                + "explicação seria lida como vigência encerrada em data desconhecida.");
            }
        }

        // Método estático que converte a referência da aplicação para a resposta.
        static ReferenciaExposta de(ReferenciaNormativa referencia) {
            return new ReferenciaExposta(
                    referencia.fonteNormativa(),
                    referencia.vigenciaInicio(),
                    referencia.vigenciaFim().orElse(null),
                    referencia.vigenciaAberta() ? VIGENCIA_ABERTA : null);
        }
    }

    // Representa a descrição que a carga dá ao NCM, para ficar ao lado da descrição da nota.
    public record NcmExposto(String ncm, String descricao, ReferenciaExposta referencia) {

        // Método estático que converte a descrição do NCM para a resposta.
        static NcmExposto de(DescricaoDeNcm descricao) {
            return new NcmExposto(
                    descricao.ncm(),
                    descricao.descricao(),
                    ReferenciaExposta.de(descricao.referencia()));
        }
    }

    // Representa um anexo a que a carga liga o NCM, com o tipo de tratamento.
    public record EnquadramentoExposto(
            String ncm, String anexo, String tipoDeTratamento, ReferenciaExposta referencia) {

        // Método estático que converte o enquadramento do NCM para a resposta.
        static EnquadramentoExposto de(EnquadramentoDoNcm enquadramento) {
            return new EnquadramentoExposto(
                    enquadramento.ncm(),
                    enquadramento.anexo(),
                    enquadramento.tipoDeTratamento(),
                    ReferenciaExposta.de(enquadramento.referencia()));
        }
    }

    // Representa o que a carga diz sobre o cClassTrib declarado. A redução vai como texto, e vem null com o motivo quando a carga não declarou nenhuma: não declarar redução é diferente de redução zero.
    public record ClassificacaoExposta(
            String codigo,
            List<String> cstsAdmitidos,
            String motivoSemCstAdmitido,
            String dispositivoLegal,
            boolean indicadorDeBeneficio,
            String percentualReducao,
            String motivoSemReducao,
            List<String> camposObrigatoriosCondicionados,
            String motivoSemCampoCondicionado,
            ReferenciaExposta referencia) {

        // Motivos escritos quando a carga não lista CST, redução ou campo exigido para o cClassTrib.
        static final String SEM_CST =
                "a carga traz este cClassTrib, mas não lista nenhum CST junto dele";
        static final String SEM_REDUCAO =
                "a carga não declarou redução para este cClassTrib. Não é redução de zero: é a fonte "
                        + "não ter dito nada a respeito";
        static final String SEM_CAMPO_CONDICIONADO =
                "a carga não lista campo que passe a ser exigido por este cClassTrib";

        // Valida que haja código, e que CSTs, campos exigidos e redução venham com conteúdo ou com o motivo de faltar.
        public ClassificacaoExposta {
            if (codigo == null || codigo.isBlank()) {
                throw new RespostaInvalida("A classificação precisa do código a que se refere.");
            }
            exigirListaExplicada(cstsAdmitidos, motivoSemCstAdmitido, "cstsAdmitidos");
            exigirListaExplicada(
                    camposObrigatoriosCondicionados,
                    motivoSemCampoCondicionado,
                    "camposObrigatoriosCondicionados");
            if ((percentualReducao == null) == (motivoSemReducao == null)) {
                throw new RespostaInvalida(
                        "Ou há redução declarada, ou há o motivo de não haver. Campo em branco seria "
                                + "lido como redução de zero.");
            }
            cstsAdmitidos = List.copyOf(cstsAdmitidos);
            camposObrigatoriosCondicionados = List.copyOf(camposObrigatoriosCondicionados);
        }

        // Método estático que converte a classificação da aplicação, pondo o motivo onde a carga não trouxe nada.
        static ClassificacaoExposta de(ClassificacaoDoCatalogo classificacao) {
            return new ClassificacaoExposta(
                    classificacao.codigo(),
                    classificacao.cstsAdmitidos(),
                    classificacao.cstsAdmitidos().isEmpty() ? SEM_CST : null,
                    classificacao.dispositivoLegal(),
                    classificacao.indicadorDeBeneficio(),
                    classificacao.percentualReducao().map(BigDecimal::toPlainString).orElse(null),
                    classificacao.percentualReducao().isPresent() ? null : SEM_REDUCAO,
                    classificacao.camposObrigatoriosCondicionados(),
                    classificacao.camposObrigatoriosCondicionados().isEmpty()
                            ? SEM_CAMPO_CONDICIONADO
                            : null,
                    ReferenciaExposta.de(classificacao.referencia()));
        }

        // Método auxiliar que exige lista não nula, com conteúdo ou com o motivo de estar vazia.
        private static void exigirListaExplicada(List<String> lista, String motivo, String campo) {
            if (lista == null) {
                throw new RespostaInvalida(
                        "A lista \"%s\" deve ser vazia quando não há nenhum, nunca nula.".formatted(campo));
            }
            if (lista.isEmpty() == (motivo == null)) {
                throw new RespostaInvalida(
                        ("A lista \"%s\" precisa ou de conteúdo, ou do motivo de estar vazia.")
                                .formatted(campo));
            }
        }
    }

    // Representa um percentual da carga e a abrangência dele.
    public record AliquotaExposta(
            String abrangencia, String percentual, ReferenciaExposta referencia) {

        // Método estático que converte a alíquota da aplicação para a resposta.
        static AliquotaExposta de(AliquotaDoCatalogo aliquota) {
            return new AliquotaExposta(
                    aliquota.abrangencia(),
                    aliquota.percentual().toPlainString(),
                    ReferenciaExposta.de(aliquota.referencia()));
        }
    }

    // Representa um tributo e o que a carga diz dele na data.
    public record TributoExposto(
            String tributo,
            String rotulo,
            String familia,
            LeituraExposta<AliquotaExposta> aliquotas) {

        // Método estático que converte o tratamento de um tributo para a resposta.
        static TributoExposto de(TratamentoDeTributo tratamento) {
            return new TributoExposto(
                    tratamento.tributo().name(),
                    tratamento.rotulo(),
                    tratamento.familia(),
                    LeituraExposta.de(tratamento.aliquotas(), AliquotaExposta::de));
        }
    }
}
