package br.edu.tcc.auditoria.dominio.regras;

import br.edu.tcc.auditoria.dominio.CodigoClassificacaoTributaria;
import br.edu.tcc.auditoria.dominio.Documento;
import br.edu.tcc.auditoria.dominio.ItemDocumento;
import br.edu.tcc.auditoria.dominio.Ncm;
import br.edu.tcc.auditoria.dominio.Severidade;
import br.edu.tcc.auditoria.dominio.ValorEmRisco;
import br.edu.tcc.auditoria.dominio.catalogo.ClassificacaoTributaria;
import br.edu.tcc.auditoria.dominio.catalogo.ContextoNormativo;
import br.edu.tcc.auditoria.dominio.catalogo.ProcedenciaNormativa;

import java.util.List;
import java.util.Optional;

// Regra R03: se o cClassTrib do item é de benefício, o NCM está em algum anexo do catálogo? Gravidade: grave. Limite conhecido: confere se o NCM está em qualquer anexo, e não no anexo certo daquele código.
public final class RegraBeneficioExigeNcmEmAnexo extends RegraDeItem {

    public static final String ID = "R03";
    public static final String VERSAO = "1.0.0";

    private final ProcedenciaNormativa cobertura;

    // Construtor que recebe o período coberto pela tabela de anexos.
    public RegraBeneficioExigeNcmEmAnexo(ProcedenciaNormativa coberturaDaTabelaDeAnexos) {
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
        return Severidade.GRAVE;
    }

    // Aplica a regra: só cobra o anexo quando o catálogo diz que o cClassTrib é de benefício, e aponta se o NCM não estiver em nenhum anexo.
    @Override
    protected Avaliacao avaliarItem(ItemDocumento item, Documento documento, ContextoNormativo contexto) {
        Optional<CodigoClassificacaoTributaria> codigo = item.codigoClassificacaoTributaria();
        if (codigo.isEmpty()) {
            return naoAvaliada(item, documento,
                    "O item não declarou cClassTrib; não há como saber se um benefício foi invocado.");
        }

        Optional<ClassificacaoTributaria> registro = contexto.classificacaoTributaria(codigo.get());
        if (registro.isEmpty()) {
            return naoAvaliada(item, documento,
                    ("O catálogo nada diz sobre o cClassTrib \"%s\" na data de emissão, então não há como "
                            + "saber se ele indica benefício.").formatted(codigo.get().valor()));
        }

        ClassificacaoTributaria classificacao = registro.get();
        if (!classificacao.indicadorDeBeneficio()) {
            // O código não é de benefício, então não precisa de anexo e o item passa na regra.
            return conforme(item, documento);
        }

        Optional<Ncm> ncm = item.ncm();
        if (ncm.isEmpty()) {
            return naoAvaliada(item, documento,
                    ("O item invoca o cClassTrib \"%s\", marcado como benefício, mas não declarou NCM; "
                            + "sem NCM não há o que procurar nos anexos.").formatted(codigo.get().valor()));
        }
        if (!cobertura.vigenteEm(documento.dataEmissao())) {
            return naoAvaliada(item, documento,
                    ("A tabela de itens de anexo carregada cobre a partir de %s%s e não alcança a data de "
                            + "emissão %s. Sem cobertura, não constar de anexo é falta de dado, não ausência "
                            + "de vínculo.").formatted(
                            cobertura.vigenciaInicio(),
                            cobertura.vigenciaFim().map(" até %s"::formatted).orElse(""),
                            documento.dataEmissao()));
        }

        List<String> anexos = AnexosDoItem.identificadoresOrdenados(contexto, ncm.get());
        if (!anexos.isEmpty()) {
            return conforme(item, documento);
        }

        return comAchado(
                item,
                documento,
                List.of(
                        doDocumento("cClassTrib", item, codigo.get().valor()),
                        doDocumento("ncm", item, ncm.get().valor()),
                        daTabela(
                                "itemAnexo",
                                AnexosDoItem.TABELA,
                                cobertura.fonteNormativa(),
                                Optional.empty(),
                                Optional.empty())),
                classificacao.dispositivoLegal(),
                classificacao.vigencia(),
                ValorEmRisco.naoCalculavel(
                        "Apurar a diferença exigiria saber qual tratamento caberia ao item, e o catálogo "
                                + "não vincula este NCM a anexo algum na data."));
    }
}
