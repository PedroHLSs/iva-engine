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

/**
 * R03 — quando o {@code cClassTrib} declarado é marcado como benefício, o NCM do
 * item consta de anexo no catálogo vigente na data?
 *
 * <p>Benefício invocado sobre mercadoria que o catálogo não vincula a anexo
 * nenhum é redução declarada sem respaldo na tabela de referência, e por isso a
 * severidade é grave.</p>
 *
 * <p>Quem diz que um código é benefício é o catálogo, pelo indicador importado
 * junto com a linha. A regra não classifica código nenhum por conta própria.</p>
 *
 * <h2>Limitação declarada: "algum anexo", não "o anexo correspondente"</h2>
 *
 * <p>A formulação exata da verificação seria confrontar o NCM contra <em>o anexo
 * correspondente àquele {@code cClassTrib}</em>. O catálogo modelado na Etapa 2
 * não guarda esse vínculo: {@code ClassificacaoTributaria} não tem campo de
 * anexo, e {@code ItemAnexo} liga NCM a anexo sem passar por {@code cClassTrib}.
 * A regra portanto verifica o vínculo do NCM a <em>qualquer</em> anexo vigente,
 * que é uma condição necessária do que se quer verificar, e nomeia na evidência
 * os anexos encontrados para leitura humana.</p>
 *
 * <p>Isso deixa passar o caso em que o NCM consta de um anexo diferente do que o
 * código invocado pressupõe. Fechar essa lacuna exige acrescentar o vínculo
 * {@code cClassTrib → anexo} ao catálogo, o que é mudança em etapa já entregue e
 * não foi feita aqui.</p>
 */
public final class RegraBeneficioExigeNcmEmAnexo extends RegraDeItem {

    public static final String ID = "R03";
    public static final String VERSAO = "1.0.0";

    private final ProcedenciaNormativa cobertura;

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
            // O código não invoca benefício: a exigência de anexo não se aplica,
            // e a regra se esgota aqui tendo sido aplicada por inteiro.
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
