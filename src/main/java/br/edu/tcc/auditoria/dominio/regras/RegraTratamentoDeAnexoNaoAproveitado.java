package br.edu.tcc.auditoria.dominio.regras;

import br.edu.tcc.auditoria.dominio.CodigoClassificacaoTributaria;
import br.edu.tcc.auditoria.dominio.Documento;
import br.edu.tcc.auditoria.dominio.Evidencia;
import br.edu.tcc.auditoria.dominio.ItemDocumento;
import br.edu.tcc.auditoria.dominio.Ncm;
import br.edu.tcc.auditoria.dominio.Severidade;
import br.edu.tcc.auditoria.dominio.ValorEmRisco;
import br.edu.tcc.auditoria.dominio.catalogo.ClassificacaoTributaria;
import br.edu.tcc.auditoria.dominio.catalogo.ContextoNormativo;
import br.edu.tcc.auditoria.dominio.catalogo.ItemAnexo;
import br.edu.tcc.auditoria.dominio.catalogo.ProcedenciaNormativa;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

// Regra R04: o NCM está em algum anexo, mas a nota foi emitida com cClassTrib de tributação integral (sem benefício)? Gravidade: informativa, porque o sistema só aponta e não aconselha; uma pessoa precisa olhar.
public final class RegraTratamentoDeAnexoNaoAproveitado extends RegraDeItem {

    public static final String ID = "R04";
    public static final String VERSAO = "1.0.0";

    private final ProcedenciaNormativa cobertura;

    // Construtor que recebe o período coberto pela tabela de anexos.
    public RegraTratamentoDeAnexoNaoAproveitado(ProcedenciaNormativa coberturaDaTabelaDeAnexos) {
        this.cobertura = exigirCobertura(coberturaDaTabelaDeAnexos, "itens de anexo");
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String versao() {
        return VERSAO;
    }

    @Override
    public Severidade severidade() {
        return Severidade.INFORMATIVA;
    }

    // Aplica a regra: se o NCM está em anexo e o cClassTrib é de tributação integral, aponta os anexos encontrados, com o tipo de tratamento que o catálogo informa.
    @Override
    protected Avaliacao avaliarItem(ItemDocumento item, Documento documento, ContextoNormativo contexto) {
        Optional<Ncm> ncm = item.ncm();
        if (ncm.isEmpty()) {
            return naoAvaliada(item, documento,
                    "O item não declarou NCM; não há como saber se algum anexo o alcança.");
        }
        if (!cobertura.vigenteEm(documento.dataEmissao())) {
            return naoAvaliada(item, documento,
                    ("A tabela de itens de anexo carregada cobre a partir de %s%s e não alcança a data de "
                            + "emissão %s. Sem cobertura, ausência de vínculo é falta de dado, e reportar "
                            + "conformidade aqui esconderia isso.").formatted(
                            cobertura.vigenciaInicio(),
                            cobertura.vigenciaFim().map(" até %s"::formatted).orElse(""),
                            documento.dataEmissao()));
        }

        List<ItemAnexo> anexos = AnexosDoItem.ordenados(contexto, ncm.get());
        if (anexos.isEmpty()) {
            // O NCM não está em nenhum anexo: não havia benefício a usar, então o item passa na regra.
            return conforme(item, documento);
        }

        Optional<CodigoClassificacaoTributaria> codigo = item.codigoClassificacaoTributaria();
        if (codigo.isEmpty()) {
            return naoAvaliada(item, documento,
                    ("O NCM %s consta de anexo no catálogo, mas o item não declarou cClassTrib; não há "
                            + "como saber qual tratamento foi aplicado.").formatted(ncm.get().valor()));
        }

        Optional<ClassificacaoTributaria> registro = contexto.classificacaoTributaria(codigo.get());
        if (registro.isEmpty()) {
            return naoAvaliada(item, documento,
                    ("O NCM %s consta de anexo no catálogo, mas o catálogo nada diz sobre o cClassTrib "
                            + "\"%s\" na data de emissão; não há como saber se o tratamento foi aproveitado.")
                            .formatted(ncm.get().valor(), codigo.get().valor()));
        }

        ClassificacaoTributaria classificacao = registro.get();
        if (!ehTributacaoIntegral(classificacao)) {
            return conforme(item, documento);
        }

        ItemAnexo primeiroAnexo = anexos.get(0);
        List<Evidencia> evidencias = new ArrayList<>();
        evidencias.add(doDocumento("ncm", item, ncm.get().valor()));
        evidencias.add(doDocumento("cClassTrib", item, codigo.get().valor()));
        for (ItemAnexo anexo : anexos) {
            evidencias.add(daTabela(
                    "itemAnexo",
                    AnexosDoItem.TABELA,
                    anexo.fonteNormativa(),
                    Optional.of("%s (%s)".formatted(anexo.identificadorDoAnexo().valor(), anexo.tipoDeTratamento())),
                    Optional.empty()));
        }

        return comAchado(
                item,
                documento,
                List.copyOf(evidencias),
                primeiroAnexo.fonteNormativa(),
                primeiroAnexo.vigencia(),
                ValorEmRisco.naoCalculavel(
                        "Quantificar o que deixou de ser aproveitado exigiria saber que tratamento o anexo "
                                + "confere, e o catálogo traz apenas o vínculo e o rótulo."));
    }

    // Método auxiliar que diz se o código é de tributação integral: o catálogo não marca como benefício e não informa redução.
    private static boolean ehTributacaoIntegral(ClassificacaoTributaria classificacao) {
        return !classificacao.indicadorDeBeneficio() && classificacao.percentualReducao().isEmpty();
    }
}
