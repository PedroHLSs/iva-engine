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

/**
 * R04 — quando o catálogo vincula o NCM do item a um anexo de tratamento
 * diferenciado, o item foi mesmo assim emitido com {@code cClassTrib} de
 * tributação integral?
 *
 * <p>É o espelho de {@link RegraBeneficioExigeNcmEmAnexo}: aquela cuida do
 * benefício invocado sem respaldo, esta do respaldo existente e não invocado —
 * direito possivelmente não aproveitado.</p>
 *
 * <p>A severidade é informativa, e isso é deliberado. O sistema aponta, não
 * aconselha: pode haver razão legítima para o contribuinte não ter aplicado o
 * tratamento, e afirmar erro aqui seria ultrapassar o que a ferramenta se propõe
 * a fazer. O apontamento diz que a situação merece leitura humana e nomeia os
 * anexos envolvidos, com o rótulo de tratamento que a fonte importada lhes deu.</p>
 *
 * <h2>O que conta como tributação integral</h2>
 *
 * <p>Um {@code cClassTrib} que o catálogo não marca como benefício e para o qual
 * não declara percentual de redução. Os dois dados vêm da linha importada; a
 * regra não decide por conta própria que código é integral e que código não é.
 * Código com redução declarada, ainda que não marcado como benefício, não é
 * integral — algum tratamento diferenciado está sendo aplicado, e não há direito
 * ocioso a relatar.</p>
 *
 * <h2>O que conta como anexo de tratamento diferenciado</h2>
 *
 * <p>Todo vínculo presente na tabela de itens de anexo. O rótulo
 * {@code tipoDeTratamento} é texto livre da fonte importada, e o domínio não
 * sabe quais rótulos significam redução, isenção ou qualquer outra coisa —
 * decidir isso em código seria afirmar sobre a norma. A tabela de anexos é, por
 * construção, a relação dos tratamentos diferenciados que o operador carregou; o
 * rótulo vai para a evidência, onde uma pessoa o lê.</p>
 */
public final class RegraTratamentoDeAnexoNaoAproveitado extends RegraDeItem {

    public static final String ID = "R04";
    public static final String VERSAO = "1.0.0";

    private final ProcedenciaNormativa cobertura;

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
            // O catálogo, dentro da cobertura declarada, não vincula este NCM a
            // anexo nenhum: não há tratamento a aproveitar, e nada a relatar.
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

    /**
     * Tributação integral segundo o que o catálogo declarou sobre o código: sem
     * marca de benefício e sem percentual de redução.
     */
    private static boolean ehTributacaoIntegral(ClassificacaoTributaria classificacao) {
        return !classificacao.indicadorDeBeneficio() && classificacao.percentualReducao().isEmpty();
    }
}
