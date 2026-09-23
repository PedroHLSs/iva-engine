package br.edu.tcc.auditoria.dominio.regras;

import br.edu.tcc.auditoria.dominio.ItemDocumento;

import java.util.Arrays;
import java.util.Optional;
import java.util.function.Function;

// Lista os nomes de campo que o catálogo pode exigir, iguais aos nomes usados em ItemDocumento. Se o catálogo usar um nome fora desta lista, a R07 responde NAO_AVALIADO.
public enum CampoDoItem {

    NCM("ncm", ItemDocumento::ncm),
    CFOP("cfop", ItemDocumento::cfop),
    CST_IBS("cstIbs", ItemDocumento::cstIbs),
    CST_CBS("cstCbs", ItemDocumento::cstCbs),
    CODIGO_CLASSIFICACAO_TRIBUTARIA(
            "codigoClassificacaoTributaria", ItemDocumento::codigoClassificacaoTributaria),
    BASE_CALCULO_IBS("baseCalculoIbs", ItemDocumento::baseCalculoIbs),
    BASE_CALCULO_CBS("baseCalculoCbs", ItemDocumento::baseCalculoCbs),
    ALIQUOTA_IBS_UF("aliquotaIbsUf", ItemDocumento::aliquotaIbsUf),
    ALIQUOTA_IBS_MUNICIPAL("aliquotaIbsMunicipal", ItemDocumento::aliquotaIbsMunicipal),
    ALIQUOTA_CBS("aliquotaCbs", ItemDocumento::aliquotaCbs),
    VALOR_IBS_UF("valorIbsUf", ItemDocumento::valorIbsUf),
    VALOR_IBS_MUNICIPAL("valorIbsMunicipal", ItemDocumento::valorIbsMunicipal),
    VALOR_CBS("valorCbs", ItemDocumento::valorCbs);

    private final String nomeNoCatalogo;
    private final Function<ItemDocumento, Optional<?>> leitura;

    // Construtor que liga cada campo ao nome usado no catálogo e ao jeito de ler esse campo no item.
    CampoDoItem(String nomeNoCatalogo, Function<ItemDocumento, Optional<?>> leitura) {
        this.nomeNoCatalogo = nomeNoCatalogo;
        this.leitura = leitura;
    }

    public String nomeNoCatalogo() {
        return nomeNoCatalogo;
    }

    // Procura o campo pelo nome escrito no catálogo; se o nome não estiver na lista, devolve vazio. O nome tem de ser exatamente igual.
    public static Optional<CampoDoItem> porNome(String nome) {
        if (nome == null) {
            return Optional.empty();
        }
        return Arrays.stream(values())
                .filter(campo -> campo.nomeNoCatalogo.equals(nome))
                .findFirst();
    }

    // Diz se o item trouxe este campo. Campo com valor zero conta como preenchido, porque vir zero é diferente de não vir nada.
    public boolean estaPreenchidoEm(ItemDocumento item) {
        return leitura.apply(item).isPresent();
    }
}
